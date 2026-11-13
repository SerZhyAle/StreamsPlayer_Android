package com.sza.streamsplayer.domain.usecase.streams

import com.sza.streamsplayer.data.local.db.StreamSourceEntity
import com.sza.streamsplayer.data.repository.StreamSourceRepository
import javax.inject.Inject

class RemoveStreamSourceUseCase @Inject constructor(
    private val repository: StreamSourceRepository,
    private val passwords: com.sza.streamsplayer.domain.repository.BroadcastPasswordStore? = null
) {
    suspend operator fun invoke(source: StreamSourceEntity) {
        repository.remove(source)
        passwords?.remove(source.id)
    }
}
