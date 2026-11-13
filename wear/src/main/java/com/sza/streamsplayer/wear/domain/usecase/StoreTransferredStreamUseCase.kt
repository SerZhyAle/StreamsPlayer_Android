package com.sza.streamsplayer.wear.domain.usecase

import com.sza.streamsplayer.wear.domain.model.WearStreamChannel
import com.sza.streamsplayer.wear.domain.model.WearStreamStoreResult
import com.sza.streamsplayer.wear.domain.model.WearStreamTransferAck
import com.sza.streamsplayer.wear.domain.model.WearStreamTransferPayload
import com.sza.streamsplayer.wear.domain.model.WearTileKind
import com.sza.streamsplayer.wear.domain.repository.WearStreamChannelRepository
import com.sza.streamsplayer.wear.util.rethrowIfCancellation
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject

/**
 * S1799: stores one stream channel transferred from the phone and answers with the outcome the
 * phone will show the user - stored, updated, or the error that prevented either.
 *
 * S1944: also hands back the channel it stored, because the phone can now ask for it to be opened and
 * the caller needs the same record the list would have used - not the raw payload.
 */
class StoreTransferredStreamUseCase @Inject constructor(
    private val repository: WearStreamChannelRepository,
    private val classifier: ClassifyWearStreamMediaKindUseCase,
    private val requestWearTileRefreshUseCase: RequestWearTileRefreshUseCase
) {

    // The ack is the phone's only view of what happened here; any persistence failure must become
    // an OUTCOME_ERROR answer rather than a crash that leaves the phone waiting out its timeout.
    /** @param binding S0038: for a broadcast, what it is the saved side of; null for a plain stream. */
    @Suppress("TooGenericExceptionCaught")
    suspend operator fun invoke(
        payload: WearStreamTransferPayload,
        binding: BroadcastBinding? = null,
    ): WearStreamStoreResult {
        if (payload.url.isBlank()) {
            return WearStreamStoreResult(
                WearStreamTransferAck(
                    requestId = payload.requestId,
                    outcome = WearStreamTransferAck.OUTCOME_ERROR,
                    message = "blank url"
                ),
                channel = null,
            )
        }
        val channel = WearStreamChannel(
            id = UUID.randomUUID().toString(),
            name = payload.name.ifBlank { payload.url },
            url = payload.url,
            // S1944: classify whenever the value is not one this watch acts on - blank or unrecognised
            // alike. Before, only a blank was reclassified, so a non-blank typo was stored raw and then
            // routed to the audio player without the URL ever being consulted.
            mediaKind = classifier.resolve(payload.mediaKind, payload.url),
            origin = WearStreamChannel.ORIGIN_PHONE,
            broadcastSourceId = binding?.sourceId,
            exchangeEntryId = binding?.entryId,
            exchangeBroadcastId = binding?.broadcastId
        )
        return try {
            val added = repository.upsertChannel(channel)
            val outcome = if (added) {
                WearStreamTransferAck.OUTCOME_STORED
            } else {
                WearStreamTransferAck.OUTCOME_UPDATED
            }
            requestWearTileRefreshUseCase(WearTileKind.STREAM)
            WearStreamStoreResult(WearStreamTransferAck(requestId = payload.requestId, outcome = outcome), channel)
        } catch (e: Exception) {
            e.rethrowIfCancellation()
            Timber.w(e, "Failed to store transferred stream")
            WearStreamStoreResult(
                WearStreamTransferAck(
                    requestId = payload.requestId,
                    outcome = WearStreamTransferAck.OUTCOME_ERROR,
                    message = e.message
                ),
                channel = null,
            )
        }
    }

    /**
     * S0038: the identity a kept broadcast carries. [entryId] is set only when the watch kept it from its own
     * account directory - a phone's entry id means nothing here.
     */
    data class BroadcastBinding(
        val sourceId: String?,
        val broadcastId: String?,
        val entryId: String? = null,
    )
}
