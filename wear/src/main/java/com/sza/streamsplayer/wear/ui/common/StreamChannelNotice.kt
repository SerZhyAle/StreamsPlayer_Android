package com.sza.streamsplayer.wear.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.wear.compose.material.Text
import com.sza.streamsplayer.wear.R
import com.sza.streamsplayer.wear.domain.model.StreamChannelReason

@Composable
fun StreamChannelNotice(reason: StreamChannelReason) {
    val message = when (reason) {
        StreamChannelReason.NARROW_LINK -> R.string.wear_stream_channel_narrow
        StreamChannelReason.UNVALIDATED_LINK -> R.string.wear_stream_channel_unverified
        StreamChannelReason.NO_LINK, StreamChannelReason.NOT_ON_WIFI -> R.string.wear_stream_channel_offline
        StreamChannelReason.BANDWIDTH_UNKNOWN -> return
    }
    Text(stringResource(message))
}
