package com.sza.streamsplayer.domain.usecase.streams

import com.sza.streamsplayer.data.local.db.StreamSourceEntity
import com.sza.streamsplayer.data.repository.StreamSourceRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * S0012: observe recently played stream channels ordered by lastPlayedAt descending.
 */
class ObserveRecentlyPlayedStreamsUseCase @Inject constructor(
    private val repository: StreamSourceRepository,
) {
    operator fun invoke(limit: Int = 100): Flow<List<StreamSourceEntity>> =
        repository.observeRecentlyPlayed(limit)
}
