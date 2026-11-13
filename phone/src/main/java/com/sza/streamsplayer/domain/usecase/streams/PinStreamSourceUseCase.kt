package com.sza.streamsplayer.domain.usecase.streams

import com.sza.streamsplayer.data.repository.StreamSourceRepository
import javax.inject.Inject

class PinStreamSourceUseCase @Inject constructor(
    private val repository: StreamSourceRepository
) {
    suspend operator fun invoke(id: String) = repository.pinToTop(id)
}
