package com.sza.streamsplayer.service

import android.content.Intent
import android.net.Uri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import com.google.gson.Gson
import com.sza.streamsplayer.MainActivity
import com.sza.streamsplayer.R
import com.sza.streamsplayer.data.wear.OpenOnPhoneNotifier
import com.sza.streamsplayer.domain.model.*
import com.sza.streamsplayer.domain.repository.WearableDataLayerRepository
import com.sza.streamsplayer.domain.usecase.ApplyWatchStreamPinsDeltaUseCase
import com.sza.streamsplayer.domain.usecase.PushWearStreamPinsUseCase
import com.sza.streamsplayer.ui.common.help.HelpOpenManager
import com.sza.streamsplayer.ui.common.help.HelpSurfaceIds
import com.sza.streamsplayer.ui.common.support.ExchangePortalLinks
import com.sza.streamsplayer.core.util.rethrowIfCancellation
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/** Stream-only extraction of the donor listener. Jobs outlive the short GMS callback. */
@AndroidEntryPoint
class PhoneWearListenerService : WearableListenerService() {
    @Inject lateinit var gson: Gson
    @Inject lateinit var bridge: WearableDataLayerRepository
    @Inject lateinit var applyPins: ApplyWatchStreamPinsDeltaUseCase
    @Inject lateinit var pushPins: PushWearStreamPinsUseCase
    @Inject lateinit var notifier: OpenOnPhoneNotifier
    private val codec = WearEventEnvelopeCodec()

    override fun onMessageReceived(event: MessageEvent) {
        applicationScope.launch {
            try {
                when (event.path) {
                    WearDataLayerPaths.EXCHANGE_LEAVE_ACK -> gson.fromJson(event.data.decodeToString(),
                        com.sza.streamsplayer.data.exchange.WatchExchangeLeaveAck::class.java)
                        ?.let { WearSyncEvents.emitExchangeLeaveAck(it) }
                    WearDataLayerPaths.EXCHANGE_HANDOVER_ACK -> WearExchangeHandoverCodec.parseAck(event.data, gson)
                        ?.let { WearSyncEvents.emitExchangeHandoverAck(it) }
                    WearDataLayerPaths.STREAM_PINS_DELTA -> {
                        val envelope = codec.decode(event.data)
                        if (envelope.eventType != WearDataLayerPaths.EVENT_STREAM_PINS_DELTA) return@launch
                        applyPins(gson.fromJson(envelope.data.decodeToString(), WearStreamPinsDeltaPayload::class.java))
                        pushPins()
                    }
                    WearDataLayerPaths.SYNC_REQUEST -> pushPins()
                    WearDataLayerPaths.STREAM_TRANSFER_ACK -> WearSyncEvents.emitStreamTransferAck(gson.fromJson(event.data.decodeToString(), WearStreamTransferAck::class.java))
                    WearDataLayerPaths.OPEN_ON_PHONE_REQUEST -> openStream(event)
                }
            } catch (e: Exception) {
                e.rethrowIfCancellation()
                Timber.w(e, "Wear stream request failed")
            }
        }
    }

    private suspend fun openStream(event: MessageEvent) {
        val request = gson.fromJson(event.data.decodeToString(), WearOpenOnPhoneRequest::class.java)
        if (request.token.startsWith(ExchangePortalLinks.WATCH_TOKEN_PREFIX)) {
            openPortalPage(event, request)
            return
        }
        if (request.token.startsWith(HelpSurfaceIds.WATCH_TOKEN_PREFIX)) {
            openHelpPage(event, request)
            return
        }
        val target = Uri.parse(request.token)
        val valid = target.scheme?.lowercase() in setOf("http", "https", "rtsp") && !target.host.isNullOrBlank()
        val outcome = if (!valid) WearOpenOnPhoneOutcome.NOT_FOUND else {
            val foreground = ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
            val shown = foreground && runCatching {
                startActivity(Intent(this, MainActivity::class.java).apply {
                    action = Intent.ACTION_VIEW
                    data = target
                    putExtra("stream_name", request.displayName)
                    putExtra("stream_kind", request.mediaKind)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                })
            }.isSuccess
            when {
                shown -> WearOpenOnPhoneOutcome.SHOWN
                notifier.notifyPendingOpen(request.token, request.displayName, target, request.mediaKind) -> WearOpenOnPhoneOutcome.NOTIFIED
                else -> WearOpenOnPhoneOutcome.REFUSED_NO_NOTIFICATION
            }
        }
        Timber.d("S0007: Open-on-phone result=%s", outcome)
        bridge.sendMessage(event.sourceNodeId, WearDataLayerPaths.OPEN_ON_PHONE_ACK,
            gson.toJson(WearOpenOnPhoneAck(request.token, outcome)).toByteArray(Charsets.UTF_8))
    }

    /**
     * S0030: the watch asked for a page of the shared media exchange portal by name. The address is built here, in the
     * language of the phone, and opens in the browser; a phone that is not in front gets a notification instead, as
     * for a file, because a background service may not start an activity.
     */
    private suspend fun openPortalPage(event: MessageEvent, request: WearOpenOnPhoneRequest) {
        val page = ExchangePortalLinks.pageById(request.token.removePrefix(ExchangePortalLinks.WATCH_TOKEN_PREFIX))
        val outcome = if (page == null) WearOpenOnPhoneOutcome.NOT_FOUND else {
            val target = Uri.parse(ExchangePortalLinks.urlFor(this, page))
            val foreground = ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
            val shown = foreground && runCatching {
                startActivity(Intent(Intent.ACTION_VIEW, target).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }.isSuccess
            when {
                shown -> WearOpenOnPhoneOutcome.SHOWN
                notifier.notifyPendingOpen(request.token, getString(R.string.portal_help_watch), target, browser = true) ->
                    WearOpenOnPhoneOutcome.NOTIFIED
                else -> WearOpenOnPhoneOutcome.REFUSED_NO_NOTIFICATION
            }
        }
        Timber.d("S0030: portal page for the watch result=%s", outcome)
        bridge.sendMessage(event.sourceNodeId, WearDataLayerPaths.OPEN_ON_PHONE_ACK,
            gson.toJson(WearOpenOnPhoneAck(request.token, outcome)).toByteArray(Charsets.UTF_8))
    }

    /**
     * S0035: the watch's Help row asked for the help page of one of its own surfaces by the surface's id. The page is
     * found by the phone's help resolver in the phone's language and opens in the help viewer; a phone that is not in
     * front gets a notification instead, as for a file. An id that is not a surface of the watch finds nothing.
     */
    private suspend fun openHelpPage(event: MessageEvent, request: WearOpenOnPhoneRequest) {
        val surfaceId = request.token.removePrefix(HelpSurfaceIds.WATCH_TOKEN_PREFIX)
        val help = surfaceId.takeIf { it.startsWith(HelpSurfaceIds.WATCH_SURFACE_PREFIX) }
            ?.let { HelpOpenManager.intentFor(this, it) }
        val outcome = if (help == null) WearOpenOnPhoneOutcome.NOT_FOUND else {
            val foreground = ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
            val shown = foreground && runCatching {
                startActivity(Intent(help).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }.isSuccess
            when {
                shown -> WearOpenOnPhoneOutcome.SHOWN
                notifier.notifyPendingOpen(request.token, getString(R.string.help_watch_notification_name), Uri.EMPTY, openIntent = help) ->
                    WearOpenOnPhoneOutcome.NOTIFIED
                else -> WearOpenOnPhoneOutcome.REFUSED_NO_NOTIFICATION
            }
        }
        bridge.sendMessage(event.sourceNodeId, WearDataLayerPaths.OPEN_ON_PHONE_ACK,
            gson.toJson(WearOpenOnPhoneAck(request.token, outcome)).toByteArray(Charsets.UTF_8))
    }

    companion object {
        private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    }
}
