package com.sza.streamsplayer.wear.ui.player.audio

import androidx.compose.runtime.*
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.material.Text
import com.sza.streamsplayer.wear.R
import com.sza.streamsplayer.wear.ui.common.*

@Composable
fun AudioPlayerScreen(onBack: () -> Unit, onOpenPhone: (String, String, String) -> Unit,
    viewModel: AudioPlayerViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.onVolumeRefresh() }
    val pinned by viewModel.isPinned.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_START) { viewModel.onHostStarted() }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.onHostStopped() }
    KeepScreenOnEffect(state.holdsDisplay)
    WearScreenScaffold {
        WearListColumn(state = rememberWearListState(), centered = true) {
            item { Text(state.mediaFile?.name.orEmpty()) }
            state.trackTitle?.let { item { Text(it) } }
            state.error?.let { item { Text(it) } }
            state.channelReason?.let { item { StreamChannelNotice(it) } }
            item { StandardWearChip(label = stringResource(if (state.isPlaying) R.string.pause else R.string.play), onClick = viewModel::togglePlayPause) }
            item { StandardWearChip(label = stringResource(R.string.wear_jump_to_live), onClick = viewModel::jumpToLive) }
            item { StandardWearChip(label = stringResource(if (pinned) R.string.wear_player_stream_unpin else R.string.wear_player_stream_pin), onClick = viewModel::togglePin) }
            item { StandardWearChip(label = stringResource(R.string.sp_open_on_phone), onClick = {
                state.mediaFile?.let { onOpenPhone(it.uri.toString(), it.name, "AUDIO") }
            }) }
            item { StandardWearChip(label = stringResource(R.string.previous), onClick = viewModel::skipToPrevious) }
            item { StandardWearChip(label = stringResource(R.string.next), onClick = viewModel::skipToNext) }
            item { StandardWearChip(label = stringResource(R.string.wear_background_playback),
                secondaryLabel = stringResource(if (state.backgroundPlaybackEnabled) R.string.enabled else R.string.disabled),
                onClick = viewModel::toggleBackgroundPlayback) }
            item { Text(stringResource(R.string.wear_audio_volume_level, state.volumeLevel, state.volumeMax)) }
            item { StandardWearChip(label = stringResource(R.string.sp_volume_down), onClick = { viewModel.onVolumeStep(false) }) }
            item { StandardWearChip(label = stringResource(R.string.sp_volume_up), onClick = { viewModel.onVolumeStep(true) }) }
            item { StandardWearChip(label = stringResource(R.string.sp_dim_screen), onClick = viewModel::toggleDimmed) }
            item { StandardWearChip(label = stringResource(R.string.wear_navigate_back), onClick = onBack) }
        }
        if (state.isDimmed) WearDimOverlay(onExit = viewModel::toggleDimmed)
    }
}
