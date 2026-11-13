package com.sza.streamsplayer.wear.service

import com.sza.streamsplayer.wear.domain.model.writeTo
import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.annotation.StringRes
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.sza.streamsplayer.wear.R
import com.sza.streamsplayer.wear.core.notification.NotificationIcons
import com.sza.streamsplayer.wear.core.notification.WearNotificationIds
import com.sza.streamsplayer.wear.data.broadcast.BroadcastDescriptorDto
import com.sza.streamsplayer.wear.data.broadcast.BroadcastDescriptorSerializer
import com.sza.streamsplayer.wear.data.broadcast.BroadcastEndpointDto
import com.sza.streamsplayer.wear.data.broadcast.WearBroadcastIdentityStore
import com.sza.streamsplayer.wear.domain.broadcast.WearBroadcastFailure
import com.sza.streamsplayer.wear.domain.broadcast.WearBroadcastSessionState
import com.sza.streamsplayer.wear.domain.broadcast.WearBroadcastSessionStateHolder
import com.sza.streamsplayer.wear.domain.model.LiveAudioEndpoint
import com.sza.streamsplayer.wear.domain.power.WearPowerManager
import com.sza.streamsplayer.wear.domain.repository.BroadcastNetworkLease
import com.sza.streamsplayer.wear.domain.repository.StreamNetworkHold
import com.sza.streamsplayer.wear.domain.usecase.GetWatchDisplayNameUseCase
import com.sza.streamsplayer.wear.service.helpers.LiveAudioLanServer
import com.sza.streamsplayer.wear.service.helpers.VoiceRecordingSessionManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

/** Owner-started microphone broadcast, extracted from the donor recording service. */
@AndroidEntryPoint
class VoiceRecordingService : Service() {
    @Inject lateinit var sessionManager: VoiceRecordingSessionManager
    @Inject lateinit var lanServer: LiveAudioLanServer
    @Inject lateinit var broadcastSession: WearBroadcastSessionStateHolder
    @Inject lateinit var descriptorSerializer: BroadcastDescriptorSerializer
    @Inject lateinit var broadcastIdentity: WearBroadcastIdentityStore
    @Inject lateinit var getWatchDisplayName: GetWatchDisplayNameUseCase
    @Inject lateinit var networkHold: StreamNetworkHold
    @Inject lateinit var wearPower: WearPowerManager
    @Inject lateinit var exchange: com.sza.streamsplayer.wear.data.exchange.WearExchangeRuntime
    @Inject lateinit var networkMonitor: com.sza.streamsplayer.wear.domain.repository.WearNetworkChannelMonitor
    private var broadcastLease: BroadcastNetworkLease? = null
    private var sessionWakeLock: PowerManager.WakeLock? = null
    private var openingJob: kotlinx.coroutines.Job? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val sessionCallbacks = object : VoiceRecordingSessionManager.Callbacks {
        override fun onSendingStarted() = Unit
        override fun onSessionFinished() = stopForegroundAndSelf()
    }
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_BROADCAST -> handleStartBroadcast()
            ACTION_STOP_BROADCAST -> serviceScope.launch {
                openingJob?.cancel()
                openingJob?.join()
                stopBroadcastSession()
            }
            else -> stopSelf()
        }
        // A killed microphone session requires another visible Start tap.
        return START_NOT_STICKY
    }
    private fun foregroundServiceType(): Int = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else 0
    private enum class NotificationStop(@StringRes val labelRes: Int, val requestCode: Int) {
        BROADCAST(R.string.wear_broadcast_notification_stop, 1)
    }
    override fun onCreate() {
        super.onCreate()
        sessionManager.attach(serviceScope, sessionCallbacks)
        serviceScope.launch { lanServer.humanListeners.collect(broadcastSession::updateLanListeners) }
    }

    override fun onDestroy() {
        // The server goes down before the session does, because the session's own teardown closes the
        // pipe and the server must never be left holding a listener over a sink that is gone.
        lanServer.stop()
        // The ticker dies before the recorder, so nothing publishes a Recording state over a session
        // that no longer exists.
        serviceScope.cancel()
        sessionManager.release()
        // Before the listen state is cleared, and unconditionally: a held network outliving the
        // process that held it is the one leak the owner cannot see and cannot undo from the watch.
        releaseBroadcastLease()
        releaseSessionWakeLock()

        clearBroadcastSessionUnlessFailed()
        super.onDestroy()
    }

    private fun acquireSessionWakeLock() {
        if (sessionWakeLock != null) {
            return
        }
        sessionWakeLock = wearPower.acquirePartialWakeLock(WearPowerManager.SESSION_WAKE_LOCK_TAG)
    }

    private fun releaseSessionWakeLock() {
        wearPower.releaseWakeLock(sessionWakeLock)
        sessionWakeLock = null
    }

    private fun handleStartBroadcast() {
        if (sessionManager.isSessionOpen || broadcastSession.state.value.isActive) {
            Timber.i("Ignoring a broadcast start: the microphone session is already open")
            return
        }
        val notification = buildNotification(R.string.wear_broadcast_notification_title, NotificationStop.BROADCAST)
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, foregroundServiceType())
        acquireSessionWakeLock()
        broadcastSession.publish(WearBroadcastSessionState.Starting)
        openingJob = serviceScope.launch {
            try { openBroadcastSession() }
            catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) {
                Timber.w(e, "Microphone broadcast failed to start")
                lanServer.stop()
                sessionManager.release()
                failBroadcast(WearBroadcastFailure.CAPTURE_FAILED)
            }
        }
    }

    private suspend fun openBroadcastSession() {
        val serverOnly = exchange.hasOutgoingEntry() && networkMonitor.channel.value.kind ==
            com.sza.streamsplayer.wear.domain.model.WearNetworkChannelKind.CELLULAR
        val lease = if (serverOnly) object : BroadcastNetworkLease {
            override val address = java.net.InetAddress.getByName("127.0.0.1")
            override fun release() = Unit
        } else networkHold.acquireBroadcastNetwork()
        if (lease == null) {
            failBroadcast(WearBroadcastFailure.NO_USABLE_NETWORK)
            return
        }
        broadcastLease = lease
        sessionManager.begin(VoiceRecordingSessionManager.Mode.LIVE_STREAM)
        if (!sessionManager.isSessionOpen) {
            // begin() already published the recorder's own error state and ended the session.
            failBroadcast(WearBroadcastFailure.CAPTURE_FAILED)
            return
        }
        val preferredPort = broadcastIdentity.preferredPort()
        // Off the main thread: binding the socket is a blocking syscall and this coroutine runs on
        // the service's Main dispatcher. The accept loop the server launches picks its own IO one.
        val endpoint = withContext(Dispatchers.IO) {
            lanServer.start(serviceScope, sessionManager.liveSink, lease.address, preferredPort)
        }
        // Recorded after the bind, not before it: a refused preferred port falls back, and the next
        // session must start from the port that actually worked.
        broadcastIdentity.rememberPort(endpoint.port)
        // Resolved here rather than inside liveStateOf: naming the node is a Data Layer round trip and
        // this is the one suspending caller.
        val watchName = getWatchDisplayName()
        broadcastSession.publish(liveStateOf(endpoint, broadcastIdentity.sourceId(), watchName))
        Timber.d("S0007: Watch microphone broadcast on air")
    }

    private fun liveStateOf(
        endpoint: LiveAudioEndpoint,
        sourceId: String,
        watchName: String
    ): WearBroadcastSessionState.Live {
        val endpointDto = BroadcastEndpointDto(
            url = endpoint.url,
            transport = "HTTP",
            mode = BroadcastDescriptorDto.MODE_AUDIO_ONLY,
            audioCodec = "aac",
            isLive = true,
            targetLatencyMs = 200L
        )
        val descriptor = BroadcastDescriptorDto(
            url = endpoint.url,
            title = watchName,
            sourceId = sourceId,
            endpoints = listOf(endpointDto),
            isLive = true,
            targetLatencyMs = 200L
        )
        return WearBroadcastSessionState.Live(
            endpoint = endpoint,
            descriptorJson = descriptorSerializer.serialize(descriptor),
            descriptorQrPayload = descriptorSerializer.serializeCompressed(descriptor)
        )
    }

    private fun failBroadcast(reason: WearBroadcastFailure) {
        releaseBroadcastLease()
        releaseSessionWakeLock()
        broadcastSession.publish(WearBroadcastSessionState.Failed(reason))
        stopForegroundAndSelf()
    }

    private suspend fun stopBroadcastSession() {
        lanServer.stop()
        sessionManager.stop()
        releaseBroadcastLease()
        releaseSessionWakeLock()
        broadcastSession.publish(WearBroadcastSessionState.Idle)
        Timber.d("S0007: Watch microphone broadcast stopped")
    }

    private fun releaseBroadcastLease() {
        broadcastLease?.release()
        broadcastLease = null
    }

    private fun clearBroadcastSessionUnlessFailed() {
        if (broadcastSession.state.value !is WearBroadcastSessionState.Failed) {
            broadcastSession.publish(WearBroadcastSessionState.Idle)
        }
    }

    private fun stopForegroundAndSelf() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun buildNotification(@StringRes titleRes: Int, stop: NotificationStop? = null): Notification {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.wear_voice_recorder_channel_name),
            NotificationManager.IMPORTANCE_LOW
        )
        manager.createNotificationChannel(channel)
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(titleRes))
            .setSmallIcon(NotificationIcons.STATUS_BAR)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOngoing(true)
            .setSilent(true)
        if (stop != null) {
            builder.addAction(NotificationIcons.STATUS_BAR, getString(stop.labelRes), stopPendingIntent(stop))
        }

        val entry = Intent(this, com.sza.streamsplayer.wear.MainActivity::class.java)
        com.sza.streamsplayer.wear.domain.model.WearLaunchTarget.Destination(com.sza.streamsplayer.wear.domain.model.WearDestinationId.BROADCAST).writeTo(entry)
        builder.setContentIntent(PendingIntent.getActivity(this, 3, entry, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
        return builder.build()
    }

    private fun stopPendingIntent(stop: NotificationStop): PendingIntent {
        val intent = when (stop) {
            NotificationStop.BROADCAST -> stopBroadcastIntent(this)

        }
        return PendingIntent.getService(
            this,
            stop.requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
    companion object {
        const val ACTION_START_BROADCAST = "com.sza.streamsplayer.wear.action.START_BROADCAST"
        const val ACTION_STOP_BROADCAST = "com.sza.streamsplayer.wear.action.STOP_BROADCAST"
        private const val NOTIFICATION_ID = WearNotificationIds.VOICE_RECORDING
        private const val CHANNEL_ID = "wear_microphone_broadcast"
        fun startBroadcastIntent(context: Context) = Intent(context, VoiceRecordingService::class.java).setAction(ACTION_START_BROADCAST)
        fun stopBroadcastIntent(context: Context) = Intent(context, VoiceRecordingService::class.java).setAction(ACTION_STOP_BROADCAST)
    }
}
