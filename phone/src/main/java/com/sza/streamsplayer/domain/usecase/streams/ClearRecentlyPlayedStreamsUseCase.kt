package com.sza.streamsplayer.domain.usecase.streams

import com.sza.streamsplayer.data.repository.StreamSourceRepository
import javax.inject.Inject

/**
 * S0012: clear all recent play history across both projection and durable state.
 */
class ClearRecentlyPlayedStreamsUseCase @Inject constructor(
    private val repository: StreamSourceRepository,
) {
    suspend operator fun invoke() {
        repository.clearRecentlyPlayed()
    }
}
