package com.sza.streamsplayer.wear.data.wear

import com.sza.streamsplayer.data.broadcast.BroadcastDescriptorParser
import com.sza.streamsplayer.wear.data.exchange.WearBroadcastDescriptorStore
import com.sza.streamsplayer.wear.data.wear.helpers.WearTransferOutcomeCoordinator
import com.sza.streamsplayer.wear.domain.model.WearBroadcastHandoffPayload
import com.sza.streamsplayer.wear.domain.model.WearStreamTransferAck
import com.sza.streamsplayer.wear.domain.model.WearStreamTransferPayload
import com.sza.streamsplayer.wear.domain.usecase.StoreTransferredStreamUseCase
import javax.inject.Inject

class WearBroadcastHandoffReceiver @Inject constructor(
    private val parser: BroadcastDescriptorParser,
    private val descriptors: WearBroadcastDescriptorStore,
    private val storeChannel: StoreTransferredStreamUseCase,
    private val coordinator: WearTransferOutcomeCoordinator,
) {
    suspend fun take(payload: WearBroadcastHandoffPayload): WearStreamTransferAck {
        val descriptor = payload.descriptor?.toString()?.let(parser::parse)
        if (descriptor == null || descriptor.mode != "AUDIO_ONLY" || descriptor.auth != null || payload.url.isBlank()) {
            return WearStreamTransferAck(payload.requestId, WearStreamTransferAck.OUTCOME_UNPLAYABLE)
        }
        val saved = try { descriptors.save(payload.url, descriptor) }
        catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (_: Exception) { false }
        if (!saved) return WearStreamTransferAck(payload.requestId, WearStreamTransferAck.OUTCOME_ERROR)
        // S0038: the source and broadcast ids let the Own list show this channel once, beside its live broadcast.
        val stored = storeChannel(
            WearStreamTransferPayload(payload.requestId, payload.name, payload.url, "AUDIO", payload.openNow),
            StoreTransferredStreamUseCase.BroadcastBinding(sourceId = descriptor.sourceId, broadcastId = payload.broadcastId),
        )
        return if (payload.openNow && stored.channel != null) coordinator.streamAck(stored.channel, payload.requestId) else stored.ack
    }
}
