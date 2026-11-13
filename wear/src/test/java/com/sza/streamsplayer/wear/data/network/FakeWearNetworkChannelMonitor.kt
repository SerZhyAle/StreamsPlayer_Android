package com.sza.streamsplayer.wear.data.network

import com.sza.streamsplayer.wear.domain.model.WearNetworkChannel
import com.sza.streamsplayer.wear.domain.repository.WearNetworkChannelMonitor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * S2488: the link value under test, writable. [WearEndpointResolver] keys its cache on this value, so
 * a test that changes networks changes exactly this.
 */
class FakeWearNetworkChannelMonitor(
    initial: WearNetworkChannel = WearNetworkChannel.NONE
) : WearNetworkChannelMonitor {

    private val state = MutableStateFlow(initial)

    override val channel: StateFlow<WearNetworkChannel> = state

    fun set(value: WearNetworkChannel) {
        state.value = value
    }
}
