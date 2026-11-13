package com.sza.streamsplayer.wear.ui.player.video

import androidx.compose.runtime.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.*
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.ui.PlayerView
import androidx.wear.compose.material.Text
import com.sza.streamsplayer.wear.R
import com.sza.streamsplayer.wear.ui.common.*

@Composable
fun VideoPlayerScreen(onBack: () -> Unit, onOpenPhone: (String, String, String) -> Unit,
    viewModel: VideoPlayerViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.onHostStopped() }
    KeepScreenOnEffect(state.isPlaying)
    WearScreenScaffold {
        WearListColumn(state = rememberWearListState(), centered = true) {
            item { Text(state.mediaFile?.name.orEmpty()) }
            item { AndroidView(factory = { PlayerView(it).apply { useController = false; player = viewModel.getPlayer() } }, modifier = Modifier.fillMaxWidth().height(120.dp)) }
            state.error?.let { item { Text(it) } }
            state.channelReason?.let { item { StreamChannelNotice(it) } }
            if (state.showBatteryWarning) item {
                StandardWearChip(label = stringResource(R.string.battery_warning), onClick = viewModel::dismissBatteryWarning)
            }
            item { StandardWearChip(label = stringResource(if (state.isPlaying) R.string.pause else R.string.play), onClick = viewModel::togglePlayPause) }
            item { StandardWearChip(label = stringResource(R.string.wear_jump_to_live), onClick = viewModel::jumpToLive) }
            item { StandardWearChip(label = stringResource(if (state.isPinned) R.string.wear_player_stream_unpin else R.string.wear_player_stream_pin), onClick = viewModel::togglePin) }
            item { StandardWearChip(label = stringResource(R.string.sp_open_on_phone), onClick = { state.mediaFile?.let { onOpenPhone(it.uri.toString(), it.name, "VIDEO") } }) }
            item { StandardWearChip(label = stringResource(R.string.wear_navigate_back), onClick = onBack) }
        }
    }
}
