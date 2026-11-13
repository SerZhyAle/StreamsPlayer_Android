package com.sza.streamsplayer.domain.model

const val DEFAULT_BROADCAST_STREAM_TITLE = "Phone Stream"
const val LEGACY_BROADCAST_STREAM_TITLE = "Phone Audio Stream"

/**
 * Lightweight application settings for StreamsPlayer.
 */
data class AppSettings(
    val broadcast: BroadcastSettings = BroadcastSettings(),
    val enableWearCompanion: Boolean = false,
    val enableBroadcasting: Boolean = true,
    val dimClockOverlayEnabled: Boolean = false,
    val hideSystemUiInFullscreen: Boolean = true,
    // S0068: how the catalog screen keeps the channel list current (Settings > General).
    val streamsCatalogRefreshPolicy: StreamsCatalogRefreshPolicy = StreamsCatalogRefreshPolicy.DEFAULT,
)

