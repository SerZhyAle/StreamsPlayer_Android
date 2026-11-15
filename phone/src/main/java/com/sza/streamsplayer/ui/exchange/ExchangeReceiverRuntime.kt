package com.sza.streamsplayer.ui.exchange

import android.app.Activity
import android.app.AlertDialog
import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.sza.streamsplayer.R
import com.sza.streamsplayer.data.broadcast.BroadcastDescriptorDto
import com.sza.streamsplayer.data.broadcast.BroadcastDescriptorParser
import com.sza.streamsplayer.data.broadcast.BroadcastDescriptorStore
import com.sza.streamsplayer.data.exchange.CastReceiverHandler
import com.sza.streamsplayer.data.exchange.DeviceExchangeEnvelope
import com.sza.streamsplayer.data.exchange.EntryStore
import com.sza.streamsplayer.data.exchange.ExchangeCertificateChangedException
import com.sza.streamsplayer.data.exchange.directory.SealedDirectoryOpener
import com.sza.streamsplayer.data.exchange.session.DeviceExchangeControlStream
import com.sza.streamsplayer.data.exchange.session.DeviceExchangeWelcome
import com.sza.streamsplayer.ui.player.FullscreenPlayerActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.lang.ref.WeakReference
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/** Application-owned receiver lifetime. Never opens an account stream for a foreign descriptor. */
@Singleton
class ExchangeReceiverRuntime @Inject constructor(
    @ApplicationContext private val context: Context,
    private val entries: EntryStore,
    private val control: DeviceExchangeControlStream,
    private val descriptors: BroadcastDescriptorStore,
    private val sealedOpener: SealedDirectoryOpener? = null,
) : Application.ActivityLifecycleCallbacks, DeviceExchangeControlStream.Listener {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val handler = CastReceiverHandler()
    private val parser = BroadcastDescriptorParser()
    private var resumed = WeakReference<Activity>(null)
    private var castPlayer = WeakReference<Activity>(null)
    private var offerJob: Job? = null
    private var pendingId: String? = null
    private var startedCount = 0
    private var initialized = false
    private var activeDialog: AlertDialog? = null
    private val preferences get() = context.getSharedPreferences("exchange_receiver", Context.MODE_PRIVATE)
    var playImmediately: Boolean
        get() = preferences.getBoolean("play_cast_immediately", false)
        set(value) { preferences.edit().putBoolean("play_cast_immediately", value).apply() }

    fun start(application: Application) {
        if (initialized) return
        initialized = true
        application.registerActivityLifecycleCallbacks(this)
        control.addListener(this)
        handler.onRequestUserConsent = { broadcast, from ->
            ask(R.string.cast_offer_title, context.getString(R.string.cast_offer_message,
                broadcast.title.orEmpty(), from), R.string.cast_offer_accept, R.string.cast_offer_decline)
        }
        handler.onStartPlayback = { broadcast -> startCast(broadcast) }
        handler.openSealed = { id, descriptor, sealed ->
            val opener = sealedOpener
            // Without an opener the entry read is discarded, and the IO hop it costs would
            // hand the rest of the cast flow to another thread; answer synchronously then.
            if (opener == null) descriptor
            else {
                val own = withContext(Dispatchers.IO) { entries.getEntry()?.deviceId }
                if (own == null) descriptor
                else withContext(Dispatchers.IO) { opener.openCast(own, id, descriptor, sealed) }
            }
        }
        handler.onStopPlayback = { id ->
            castPlayer.get()?.takeIf { it.intent.getStringExtra(EXTRA_CAST_ID) == id }?.finish()
            castPlayer.clear()
        }
    }

    suspend fun confirmCertificate(fingerprint: String, hostPort: String): Boolean = withContext(Dispatchers.Main.immediate) {
        ask(R.string.cert_pin_title, "$hostPort\n" + context.getString(R.string.cert_pin_message, fingerprint),
            R.string.cert_pin_accept, R.string.action_cancel, icon = R.drawable.ic_exchange_mark_small)
    }

    suspend fun askBroadcastPassword(refused: Boolean): String? = withContext(Dispatchers.Main.immediate) {
        val activity = resumed.get() as? androidx.appcompat.app.AppCompatActivity ?: return@withContext null
        if (activity.isFinishing || activity.isDestroyed) return@withContext null
        val prompt = com.sza.streamsplayer.ui.streams.helpers.StreamBroadcastPasswordPromptManager(activity)
        try { prompt.ask(refused) } finally { prompt.close() }
    }

    fun showPlaybackFailure(error: Exception) {
        val activity = resumed.get()?.takeUnless { it.isFinishing || it.isDestroyed } ?: return
        val builder = AlertDialog.Builder(activity)
        if (com.sza.streamsplayer.domain.player.BroadcastPlaybackPairs.isPasswordRefusal(error)) {
            builder.setTitle(R.string.broadcast_password_title)
                .setMessage(R.string.broadcast_password_wrong)
                .setPositiveButton(android.R.string.ok, null)
        } else if (error is com.sza.streamsplayer.data.exchange.UnsupportedBroadcastAuthException) {
            builder.setMessage(R.string.broadcast_auth_unsupported)
                .setPositiveButton(android.R.string.ok, null)
        } else if (error is ExchangeCertificateChangedException) {
            builder.setIcon(R.drawable.ic_exchange_mark_small)
                .setTitle(R.string.cert_changed_title)
                .setMessage(activity.getString(R.string.cert_changed_message, error.pinned, error.presented))
                .setPositiveButton(android.R.string.ok, null)
        } else {
            builder.setTitle(R.string.streams_unavailable_title)
                .setMessage(R.string.stream_not_available)
                .setPositiveButton(android.R.string.ok, null)
        }
        if (activity is androidx.fragment.app.FragmentActivity) {
            builder.setNeutralButton(R.string.exchange_diagnose_button) { _, _ ->
                val failure = com.sza.streamsplayer.data.exchange.ExchangeLocalFailureClassifier.of(error)
                ExchangeDiagnosisDialog.show(
                    fragmentManager = activity.supportFragmentManager,
                    message = failure.message,
                    host = null,
                )
            }
        }
        builder.show()
    }

    /** [icon] 0 draws no picture; the certificate prompt passes the exchange mark (S0039, EXCHANGE-MARK rule 8 (c)). */
    private suspend fun ask(title: Int, message: String, accept: Int, decline: Int, icon: Int = 0): Boolean {
        val activity = resumed.get()?.takeUnless { it.isFinishing || it.isDestroyed } ?: return false
        return suspendCancellableCoroutine { continuation ->
            val dialog = AlertDialog.Builder(activity).setIcon(icon).setTitle(title).setMessage(message)
                .setPositiveButton(accept) { _, _ -> if (continuation.isActive) continuation.resume(true) }
                .setNegativeButton(decline) { _, _ -> if (continuation.isActive) continuation.resume(false) }
                .setOnCancelListener { if (continuation.isActive) continuation.resume(false) }.create()
            activeDialog?.cancel()
            activeDialog = dialog
            dialog.setOnDismissListener {
                if (activeDialog === dialog) activeDialog = null
                if (continuation.isActive) continuation.resume(false)
            }
            continuation.invokeOnCancellation { activity.runOnUiThread { dialog.dismiss() } }
            dialog.show()
        }
    }

    override fun onEnvelope(envelope: DeviceExchangeEnvelope) {
        scope.launch {
            when (envelope.type) {
                DeviceExchangeEnvelope.TYPE_CAST_OFFER -> {
                    offerJob?.cancel()
                    pendingId = envelope.obj("broadcast")?.get("broadcastId")?.takeIf { it.isJsonPrimitive }?.asString
                    handler.playImmediately = playImmediately && resumed.get() != null
                    offerJob = scope.launch {
                        handler.handleCastOffer(envelope, parser, { answer ->
                            scope.launch(Dispatchers.IO) { control.post(answer) }
                        })
                    }
                }
                DeviceExchangeEnvelope.TYPE_CAST_STOP, DeviceExchangeEnvelope.TYPE_BROADCAST_END -> {
                    stop(envelope.string("broadcastId").orEmpty())
                }
                DeviceExchangeEnvelope.TYPE_CHANGED -> {
                    val ids = envelope.obj("removals")?.getAsJsonArray("broadcastIds")
                    ids?.forEach { if (it.isJsonPrimitive) stop(it.asString) }
                }
            }
        }
    }

    override fun onSessionOpened(welcome: DeviceExchangeWelcome) {
        scope.launch(Dispatchers.IO) { control.post(DeviceExchangeEnvelope.of(DeviceExchangeEnvelope.TYPE_SUBSCRIBE)) }
    }

    override fun onSessionClosed() {
        scope.launch { offerJob?.cancel(); pendingId = null }
    }

    private fun stop(id: String) {
        if (id == pendingId) { offerJob?.cancel(); pendingId = null }
        handler.handleBroadcastEnded(id)
    }

    private fun startCast(broadcast: BroadcastDescriptorDto) {
        val activity = resumed.get()?.takeUnless { it.isFinishing || it.isDestroyed } ?: return
        val id = handler.currentBroadcastId ?: return
        descriptors.saveDescriptor(broadcast.url, broadcast.sourceId, broadcast)
        activity.startActivity(Intent(activity, FullscreenPlayerActivity::class.java).apply {
            putExtra(FullscreenPlayerActivity.EXTRA_CHANNEL_URL, broadcast.url)
            putExtra(FullscreenPlayerActivity.EXTRA_CHANNEL_TITLE, broadcast.title.orEmpty())
            putExtra(EXTRA_CAST_ID, id)
        })
    }

    suspend fun playbackActive(active: Boolean) = withContext(Dispatchers.IO) {
        control.setEntry(entries.getEntry())
        control.updatePlaybackActive(active)
    }
    fun playbackStopped() { scope.launch(Dispatchers.IO) { control.updatePlaybackActive(false) } }
    fun refreshAccount() { scope.launch(Dispatchers.IO) { control.setEntry(connectedEntry()) } }

    private suspend fun connectedEntry() = entries.getAccountEntry()
        ?.takeIf { it.state == com.sza.streamsplayer.data.exchange.ExchangeAccountState.Connected }
        ?.toDeviceExchangeAccountEntry()

    override fun onActivityStarted(activity: Activity) {
        startedCount++
        scope.launch(Dispatchers.IO) {
            control.setEntry(connectedEntry())
            control.updateActivityForeground(startedCount > 0)
        }
    }
    override fun onActivityResumed(activity: Activity) {
        resumed = WeakReference(activity)
        activity.intent.getStringExtra(EXTRA_CAST_ID)?.let { id ->
            if (id != handler.currentBroadcastId) activity.finish() else castPlayer = WeakReference(activity)
        }
        refreshAccount()
    }
    override fun onActivityPaused(activity: Activity) {
        if (resumed.get() === activity) {
            activeDialog?.cancel()
            resumed.clear()
        }
    }
    override fun onActivityStopped(activity: Activity) {
        startedCount = (startedCount - 1).coerceAtLeast(0)
        scope.launch(Dispatchers.IO) { control.updateActivityForeground(startedCount > 0) }
    }
    override fun onActivityDestroyed(activity: Activity) {
        if (castPlayer.get() === activity) castPlayer.clear()
    }
    override fun onActivityCreated(activity: Activity, state: Bundle?) {}
    override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) {}

    companion object { const val EXTRA_CAST_ID = "exchange_cast_id" }
}
