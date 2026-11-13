package com.sza.streamsplayer.wear.ui.common

import androidx.compose.runtime.compositionLocalOf
import com.sza.streamsplayer.wear.domain.model.WearBackground

data class WearWallpaperState(
    val background: WearBackground = WearBackground.None,
    val showsWallpaper: Boolean = false,
    val isResumed: Boolean = true
)

val LocalWearWallpaperState = compositionLocalOf { WearWallpaperState() }
