package com.sza.streamsplayer.wear.ui.pictures

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.sza.streamsplayer.wear.R
import com.sza.streamsplayer.wear.domain.delivery.DeliverableSet
import com.sza.streamsplayer.wear.ui.common.StandardWearAlertDialog
import com.sza.streamsplayer.wear.ui.common.StandardWearChip
import com.sza.streamsplayer.wear.ui.common.WearListColumn
import com.sza.streamsplayer.wear.ui.common.WearScreenScaffold
import com.sza.streamsplayer.wear.ui.common.rememberWearListState

private val STATUS_HORIZONTAL_PADDING = 8.dp

/**
 * S0064: the one place on the watch that names the two channel-picture sets. Each shows its state, its
 * published size once the manifest answers, and the button that fits: Download for a set the watch does
 * not hold, Remove for one it does. Remove asks first and says the watch will not fetch the set again
 * until it is downloaded here. Opening the screen starts no download and no removal.
 */
@Composable
fun WearChannelPicturesScreen(viewModel: WearChannelPicturesViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var confirming by remember { mutableStateOf<DeliverableSet?>(null) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.reload() }

    WearScreenScaffold {
        WearListColumn(state = rememberWearListState()) {
            item { Text(stringResource(R.string.sp_channel_pictures), textAlign = TextAlign.Center) }
            // The scope of the list is not composable: every string is read inside its own item.
            for (set in WEAR_PICTURE_SETS) {
                val row = state.rowOf(set)
                item(key = "head-${set.name}") {
                    SetHeading(
                        title = stringResource(titleRes(set)),
                        status = row.status,
                        megabytes = state.megabytes[set],
                    )
                }
                if (row.action != WearChannelPictureAction.NONE) {
                    item(key = "download-${set.name}") {
                        val title = stringResource(titleRes(set))
                        val label = stringResource(
                            if (row.action == WearChannelPictureAction.RETRY) R.string.wear_pictures_action_retry
                            else R.string.wear_pictures_action_download
                        )
                        val described = stringResource(R.string.wear_pictures_action_for, label, title)
                        StandardWearChip(
                            label = label,
                            onClick = { viewModel.download(set) },
                            modifier = Modifier.semantics { contentDescription = described },
                        )
                    }
                }
                if (row.canRemove) {
                    item(key = "remove-${set.name}") {
                        val title = stringResource(titleRes(set))
                        val label = stringResource(R.string.wear_pictures_action_remove)
                        val described = stringResource(R.string.wear_pictures_action_for, label, title)
                        StandardWearChip(
                            label = label,
                            onClick = { confirming = set },
                            modifier = Modifier.semantics { contentDescription = described },
                            primary = false,
                        )
                    }
                }
            }
        }
    }

    confirming?.let { set ->
        val megabytes = state.megabytes[set]
        val explanation = stringResource(removeMessageRes(set))
        val freed = megabytes?.let { stringResource(R.string.wear_pictures_remove_size, it) }
        val message = if (freed == null) explanation else "$explanation $freed"
        StandardWearAlertDialog(
            show = true,
            title = stringResource(R.string.wear_pictures_remove_title),
            message = message,
            confirmLabel = stringResource(R.string.wear_pictures_action_remove),
            isDestructive = true,
            onConfirm = {
                viewModel.remove(set)
                confirming = null
            },
            onDismissRequest = { confirming = null },
        )
    }
}

@Composable
private fun SetHeading(title: String, status: WearChannelPictureStatus, megabytes: Int?) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = STATUS_HORIZONTAL_PADDING)) {
        Text(
            text = title,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.caption1,
        )
        statusRes(status)?.let { res ->
            Text(
                text = stringResource(res),
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.caption2,
            )
        }
        if (megabytes != null) {
            Text(
                text = stringResource(R.string.wear_pictures_size, megabytes),
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.caption2,
            )
        }
    }
}

@StringRes
private fun titleRes(set: DeliverableSet): Int = when (set) {
    DeliverableSet.CHANNEL_PREVIEW_ATLAS -> R.string.wear_pictures_previews_title
    DeliverableSet.STREAM_LOGO_ATLAS -> R.string.wear_pictures_logos_title
}

@StringRes
private fun removeMessageRes(set: DeliverableSet): Int = when (set) {
    DeliverableSet.CHANNEL_PREVIEW_ATLAS -> R.string.wear_pictures_remove_message_previews
    DeliverableSet.STREAM_LOGO_ATLAS -> R.string.wear_pictures_remove_message_logos
}

/** The status line of a row; null while the disk is being read, when the row says nothing yet. */
private fun statusRes(status: WearChannelPictureStatus): Int? = when (status) {
    WearChannelPictureStatus.CHECKING -> null
    WearChannelPictureStatus.NOT_INSTALLED -> R.string.wear_pictures_status_not_installed
    WearChannelPictureStatus.INSTALLED -> R.string.wear_pictures_status_installed
    WearChannelPictureStatus.DOWNLOADING -> R.string.wear_pictures_status_downloading
    WearChannelPictureStatus.FAILED -> R.string.wear_pictures_status_failed
}
