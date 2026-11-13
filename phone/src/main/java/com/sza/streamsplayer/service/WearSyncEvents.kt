package com.sza.streamsplayer.service

import com.sza.streamsplayer.domain.model.WearPlaybackStatePayload
import com.sza.streamsplayer.domain.model.WearStreamTransferAck
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Process-wide event bus for messages received from the watch companion.
 *
 * Lives in `src/main` (GMS-free, pure coroutines) so the main-flavor `WearSyncViewModel` collector
 * compiles for every flavor, while the GMS-backed `PhoneWearListenerService` emitter (S0403
 * `wearGms` source set) publishes into it. Non-Wear flavors keep these flows inert - nothing emits.
 *
 * S1031: the mutable backing flows are private; collectors read the read-only [SharedFlow] views and
 * the emitter publishes through the explicit `emit*` methods, so a single owner controls emission.
 */
object WearSyncEvents {
    private val _exchangeLeaveAckFlow = MutableSharedFlow<com.sza.streamsplayer.data.exchange.WatchExchangeLeaveAck>(extraBufferCapacity = 4)
    val exchangeLeaveAckFlow = _exchangeLeaveAckFlow.asSharedFlow()
    suspend fun emitExchangeLeaveAck(ack: com.sza.streamsplayer.data.exchange.WatchExchangeLeaveAck) = _exchangeLeaveAckFlow.emit(ack)
    private val _exchangeHandoverAckFlow = MutableSharedFlow<com.sza.streamsplayer.domain.model.WearExchangeHandoverAck>(extraBufferCapacity = 4)
    val exchangeHandoverAckFlow = _exchangeHandoverAckFlow.asSharedFlow()
    suspend fun emitExchangeHandoverAck(ack: com.sza.streamsplayer.domain.model.WearExchangeHandoverAck) = _exchangeHandoverAckFlow.emit(ack)
    private val _streamTransferAckFlow = MutableSharedFlow<WearStreamTransferAck>(extraBufferCapacity = 4)
    val streamTransferAckFlow = _streamTransferAckFlow.asSharedFlow()
    private val _watchPlaybackStateFlow = MutableSharedFlow<WearPlaybackStatePayload?>(extraBufferCapacity = 1)
    val watchPlaybackStateFlow = _watchPlaybackStateFlow.asSharedFlow()
    suspend fun emitStreamTransferAck(ack: WearStreamTransferAck) = _streamTransferAckFlow.emit(ack)
    suspend fun emitWatchPlaybackState(state: WearPlaybackStatePayload?) = _watchPlaybackStateFlow.emit(state)
}
