package com.sza.streamsplayer.wear.ui.player.common

import com.sza.streamsplayer.wear.domain.model.WearOpenOnPhoneRequest
import com.sza.streamsplayer.wear.domain.repository.WearOpenOnPhoneRepository
import javax.inject.Inject

/** StreamsPlayer hands a station to its paired phone through the donor acknowledged bridge. */
class PlayerCastManager @Inject constructor(private val phone: WearOpenOnPhoneRepository) {
    val message = kotlinx.coroutines.flow.MutableStateFlow<Int?>(null)
    fun dismissMessage() { message.value = null }
    suspend fun openOnPhone(url: String, name: String) = phone.requestOpen(WearOpenOnPhoneRequest(url, name))
}
