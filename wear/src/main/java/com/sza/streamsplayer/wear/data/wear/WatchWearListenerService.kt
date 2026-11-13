package com.sza.streamsplayer.wear.data.wear
import com.google.android.gms.wearable.*
import com.google.gson.Gson
import com.sza.streamsplayer.wear.data.repository.WearPhonePinsRepository
import com.sza.streamsplayer.wear.di.ApplicationScope
import com.sza.streamsplayer.wear.domain.model.*
import com.sza.streamsplayer.wear.domain.usecase.StoreTransferredStreamUseCase
import com.sza.streamsplayer.wear.domain.usecase.SendStreamPinsDeltaUseCase
import com.sza.streamsplayer.wear.util.errorUnlessCancellation
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import javax.inject.Inject
@AndroidEntryPoint
class WatchWearListenerService : WearableListenerService() {
    private val envelopeCodec = WearEventEnvelopeCodec()
    @Inject lateinit var coordinator: com.sza.streamsplayer.wear.data.wear.helpers.WearTransferOutcomeCoordinator
    @Inject lateinit var gson: Gson
    @Inject lateinit var exchangeHandover: WearExchangeHandoverReceiver
    @Inject lateinit var exchangeLeave: WearExchangeLeaveReceiver
    @Inject lateinit var broadcastHandoff: WearBroadcastHandoffReceiver
    @Inject lateinit var wearPhonePinsRepository: WearPhonePinsRepository
    @Inject lateinit var storeTransferredStreamUseCase: StoreTransferredStreamUseCase
    @Inject lateinit var sendPins: SendStreamPinsDeltaUseCase
    @Inject @ApplicationScope lateinit var applicationScope: CoroutineScope
    override fun onDataChanged(events: DataEventBuffer) {
        for (event in events) {
            if (event.type != DataEvent.TYPE_CHANGED || event.dataItem.uri.path != WearDataLayerPaths.STREAM_PINS) continue
            val bytes = DataMapItem.fromDataItem(event.dataItem).dataMap.getByteArray("payload") ?: continue
            handleStreamPinsPush(bytes)
        }
    }
    override fun onCapabilityChanged(info: CapabilityInfo) {
        applicationScope.launch { sendPins() }
    }
    override fun onMessageReceived(event: MessageEvent) {
        when (event.path) {
            WearDataLayerPaths.STREAM_BROADCAST_PASS -> applicationScope.launch {
                try {
                    val envelope = envelopeCodec.decode(event.data)
                    val payload = gson.fromJson(envelope.data.decodeToString(), WearBroadcastHandoffPayload::class.java)
                    sendStreamTransferAck(event.sourceNodeId, broadcastHandoff.take(payload))
                } catch (e: Exception) { e.errorUnlessCancellation("Watch broadcast hand-off failed") }
            }
            WearDataLayerPaths.EXCHANGE_LEAVE -> applicationScope.launch {
                try { exchangeLeave.handle(event.sourceNodeId, event.data) }
                catch (e: Exception) { e.errorUnlessCancellation("Watch exchange removal failed") }
            }
            WearDataLayerPaths.EXCHANGE_HANDOVER -> applicationScope.launch {
                try { exchangeHandover.handle(event.sourceNodeId, event.data) }
                catch (e: Exception) { e.errorUnlessCancellation("Watch exchange hand-over failed") }
            }
            WearDataLayerPaths.PLAYBACK_CMD -> handlePlaybackCommand(event.data)
            WearDataLayerPaths.STREAM_TRANSFER -> applicationScope.launch {
                try {
                    val envelope = envelopeCodec.decode(event.data)
                    val payload = gson.fromJson(envelope.data.decodeToString(), WearStreamTransferPayload::class.java)
                    val result = storeTransferredStreamUseCase(payload)
                    val ack = if (payload.openNow && result.channel != null) coordinator.streamAck(result.channel, payload.requestId) else result.ack
                    sendStreamTransferAck(event.sourceNodeId, ack)
                } catch (e: Exception) { e.errorUnlessCancellation("Stream transfer failed") }
            }
        }
    }
    private fun handleStreamPinsPush(payloadBytes: ByteArray) {
        applicationScope.launch {
            try {
                val envelope = envelopeCodec.decode(payloadBytes)
                val payload = gson.fromJson(envelope.data.decodeToString(), WearStreamPinsPayload::class.java)
                wearPhonePinsRepository.replaceAll(payload.identities)
            } catch (e: CancellationException) {
                // The service scope was torn down; swallowing this would leave the coroutine machinery
                // believing the job is still live (S1363/S1889/S1910).
                throw e
            } catch (e: Exception) {
                Timber.w(e, "Failed to apply stream pins push - keeping the previously stored set")
            }
        }
    }
    private fun handlePlaybackCommand(data: ByteArray) {
        applicationScope.launch {
            try {
                val envelope = envelopeCodec.decode(data)
                val commandName = gson.fromJson(envelope.data.decodeToString(), String::class.java)
                val command = WearPlaybackCommand.valueOf(commandName)
                WatchPlaybackCommandEvents.commandFlow.emit(command)
            } catch (e: Exception) {
                e.errorUnlessCancellation("Failed to deserialize playback command")
            }
        }
    }
    private suspend fun sendStreamTransferAck(nodeId: String, ack: WearStreamTransferAck) {
        if (nodeId.isBlank()) return
        try {
            Wearable.getMessageClient(this)
                .sendMessage(
                    nodeId,
                    WearDataLayerPaths.STREAM_TRANSFER_ACK,
                    gson.toJson(ack).toByteArray(Charsets.UTF_8)
                )
                .await()
        } catch (e: Exception) {
            e.errorUnlessCancellation("Failed to send stream transfer ack")
        }
    }
}
object WatchPlaybackCommandEvents {
    val commandFlow = MutableSharedFlow<WearPlaybackCommand>(extraBufferCapacity = 4)
}

object WatchStreamOpenEvents {
    val requestFlow = MutableSharedFlow<WearStreamChannel>(extraBufferCapacity = 1)
    val openedFlow = MutableSharedFlow<String>(extraBufferCapacity = 1)
}
