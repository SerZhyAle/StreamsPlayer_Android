package com.sza.streamsplayer.ui.streams.helpers

import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import com.sza.streamsplayer.data.local.db.StreamSourceEntity

/**
 * Constructor holders for [StreamInlineAudioManager]. Grouping the view handles and the host callbacks
 * keeps its constructor clean and modular.
 */
data class StreamInlineAudioViews(
    val miniControl: View,
    val titleView: TextView,
    val playStopButton: ImageButton,
    val iconView: ImageView? = null,
    val closeButton: ImageButton? = null,
    val sleepTimerButton: View? = null,
)

/**
 * Every field defaults to a no-op, so a host that only cares about a subset supplies just that subset.
 */
data class StreamInlineAudioCallbacks(
    val onPlayingChanged: (String?) -> Unit = {},
    val onPlaybackStateChanged: (StreamSourceEntity?, Boolean) -> Unit = { _, _ -> },
    // Fired when the inline stream fails to play (no response, dead URL).
    val onError: (StreamSourceEntity) -> Unit = {},
    // Fired once per play when the stream actually starts playing (ground-truth "OK" outcome).
    val onSuccess: (StreamSourceEntity) -> Unit = {},
    // Fired with this manager's own playing id and the current now-playing track line (or null)
    val onNowPlayingChanged: (playingId: String?, track: String?) -> Unit = { _, _ -> },
    // Fired when user explicitly performs a manual full stop of the player
    val onManualFullStop: () -> Unit = {},
)
