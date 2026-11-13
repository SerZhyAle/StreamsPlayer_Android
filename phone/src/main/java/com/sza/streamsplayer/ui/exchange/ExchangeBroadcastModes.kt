package com.sza.streamsplayer.ui.exchange

import androidx.annotation.StringRes
import com.sza.streamsplayer.R

/**
 * S0030: the words a person reads for a broadcast mode of the directory. The mode on the wire (`AUDIO_ONLY`,
 * `VIDEO_AUDIO`, `VIDEO_ONLY`) is a wire value and never a word (DEVICE-EXCHANGE item CV); an unknown value shows
 * no mode at all rather than the raw value.
 */
object ExchangeBroadcastModes {
    @StringRes
    fun labelRes(mode: String): Int? = when (mode) {
        "AUDIO_ONLY" -> R.string.broadcast_entry_mode_audio_only
        "VIDEO_AUDIO" -> R.string.broadcast_entry_mode_video_audio
        "VIDEO_ONLY" -> R.string.broadcast_entry_mode_video_only
        else -> null
    }
}
