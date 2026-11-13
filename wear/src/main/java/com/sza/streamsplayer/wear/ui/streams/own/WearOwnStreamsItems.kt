package com.sza.streamsplayer.wear.ui.streams.own

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyListScope
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.sza.streamsplayer.wear.R
import com.sza.streamsplayer.wear.ui.common.ExchangeMarkPlate
import com.sza.streamsplayer.wear.ui.common.StandardWearChip
import com.sza.streamsplayer.wear.ui.streams.OwnNotice
import com.sza.streamsplayer.wear.ui.streams.OwnStatus
import com.sza.streamsplayer.wear.ui.streams.OwnStreamsState
import java.util.Date

private val ICON_SIZE = 24.dp

/** S0038: the wearer's actions on the live side of Own. */
data class OwnStreamsActions(
    val onRefresh: () -> Unit,
    val onLiveClick: (WearLiveBroadcast) -> Unit,
    val onSave: (WearLiveBroadcast) -> Unit,
)

/**
 * S0038: the live side of the Own category - the status line, the answer to the last action, and the broadcasts
 * of the account's other devices grouped by device - followed by the heading of the saved group, whose rows are
 * the screen's ordinary stream rows.
 */
fun ScalingLazyListScope.ownStreamsSection(
    own: OwnStreamsState,
    searching: Boolean,
    actions: OwnStreamsActions,
) {
    item(key = "own:status") { OwnStatusLine(own, actions.onRefresh) }
    own.notice?.let { notice -> item(key = "own:notice") { OwnCaption(stringResource(notice.text())) } }
    if (own.status != OwnStatus.NO_SERVER && own.status != OwnStatus.SIGN_IN) {
        item(key = "own:live") { OwnHeading(stringResource(R.string.wear_own_section_live)) }
        val loaded = own.status == OwnStatus.CURRENT || own.status == OwnStatus.STALE
        if (own.live.isEmpty && loaded) {
            item(key = "own:live-empty") {
                OwnCaption(
                    stringResource(if (searching) R.string.wear_own_filtered_empty else R.string.wear_own_live_empty),
                )
            }
        }
        for (device in own.live.devices) {
            item(key = "own:device:${device.deviceId}") { OwnDeviceHeader(device) }
            items(device.broadcasts, key = { "own:live:${it.entryId}:${it.broadcastId}" }) { row ->
                OwnLiveRow(row, actions)
            }
        }
    }
    item(key = "own:saved") { OwnHeading(stringResource(R.string.wear_own_section_saved)) }
}

/** S0038: shown under the saved heading when no personal channel is left to list. */
fun ScalingLazyListScope.ownSavedEmpty(searching: Boolean) {
    item(key = "own:saved-empty") {
        OwnCaption(stringResource(if (searching) R.string.wear_own_filtered_empty else R.string.wear_own_saved_empty))
    }
}

@Composable
private fun OwnStatusLine(own: OwnStreamsState, onRefresh: () -> Unit) {
    val context = LocalContext.current
    val updated = own.lastUpdatedAtMs?.let { DateFormat.getTimeFormat(context).format(Date(it)) }
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // S0039: EXCHANGE-MARK rule 8 (c) - no server, connect again and a failure are statuses about the server.
        when (own.status) {
            OwnStatus.NO_SERVER -> {
                OwnServerMark()
                OwnCaption(stringResource(R.string.wear_own_status_no_server))
            }
            OwnStatus.SIGN_IN -> {
                OwnServerMark()
                OwnCaption(stringResource(R.string.wear_own_status_sign_in))
            }
            OwnStatus.LOADING -> {
                CircularProgressIndicator(modifier = Modifier.size(ICON_SIZE))
                Spacer(modifier = Modifier.height(4.dp))
                OwnCaption(stringResource(R.string.wear_own_status_loading))
            }
            OwnStatus.FAILED -> {
                OwnServerMark()
                OwnCaption(stringResource(R.string.wear_own_status_unreachable))
                OwnRetry(own.refreshing, onRefresh)
            }
            OwnStatus.STALE -> {
                OwnCaption(
                    updated?.let { stringResource(R.string.wear_own_status_stale, it) }
                        ?: stringResource(R.string.wear_own_status_stale_unknown),
                )
                if (own.failed) OwnRetry(own.refreshing, onRefresh)
            }
            OwnStatus.CURRENT -> updated?.let { OwnCaption(stringResource(R.string.wear_own_status_updated, it)) }
        }
    }
}

/** The exchange server's mark above a status caption, as the loading state shows its ring there. */
@Composable
private fun OwnServerMark() {
    ExchangeMarkPlate()
    Spacer(modifier = Modifier.height(4.dp))
}

@Composable
private fun OwnRetry(refreshing: Boolean, onRefresh: () -> Unit) {
    if (refreshing) {
        CircularProgressIndicator(modifier = Modifier.size(ICON_SIZE))
    } else {
        StandardWearChip(label = stringResource(R.string.wear_own_retry), onClick = onRefresh, primary = false)
    }
}

@Composable
private fun OwnHeading(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.title3,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 2.dp).semantics { heading() },
    )
}

@Composable
private fun OwnCaption(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.caption2,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
    )
}

@Composable
private fun OwnDeviceHeader(device: WearLiveDevice) {
    val name = device.deviceName ?: stringResource(R.string.wear_own_device_unnamed)
    val presence = stringResource(device.presence.text())
    Text(
        text = "$name · $presence",
        style = MaterialTheme.typography.caption1,
        color = MaterialTheme.colors.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp).semantics { heading() },
    )
}

/**
 * One live broadcast: its title, then kind, the device's state and Saved - or why the watch cannot play it. An
 * unsaved row is followed by its own Save chip, so the crown reaches it like any other row.
 */
@Composable
private fun OwnLiveRow(row: WearLiveBroadcast, actions: OwnStreamsActions) {
    val title = row.title ?: stringResource(R.string.wear_own_live_untitled)
    val details = buildList {
        row.kind?.let { add(stringResource(it.text())) }
        add(stringResource(row.presence.text()))
        if (row.saved != null) add(stringResource(R.string.wear_own_live_saved_badge))
    }.joinToString(" · ")
    val secondary = row.limit?.let { stringResource(it.rowText()) } ?: details
    val device = row.deviceName ?: stringResource(R.string.wear_own_device_unnamed)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        StandardWearChip(
            label = title,
            secondaryLabel = secondary,
            onClick = { actions.onLiveClick(row) },
            primary = row.playable,
            modifier = Modifier.semantics { contentDescription = "$title, $device, $secondary" },
        )
        if (row.saved == null && row.playable) {
            val saveDescription = stringResource(R.string.wear_own_live_save_desc, title)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                StandardWearChip(
                    label = stringResource(R.string.wear_own_live_save),
                    onClick = { actions.onSave(row) },
                    primary = false,
                    icon = {
                        Icon(
                            imageVector = Icons.Filled.BookmarkAdd,
                            contentDescription = null,
                            modifier = Modifier.size(ICON_SIZE),
                        )
                    },
                    modifier = Modifier.semantics { contentDescription = saveDescription },
                )
            }
        }
    }
}

private fun WearLiveKind.text(): Int = when (this) {
    WearLiveKind.AUDIO -> R.string.wear_own_kind_audio
    WearLiveKind.VIDEO -> R.string.wear_own_kind_video
}

private fun WearLivePresence.text(): Int = when (this) {
    WearLivePresence.ONLINE -> R.string.wear_own_presence_online
    WearLivePresence.OFFLINE -> R.string.wear_own_presence_offline
    WearLivePresence.UNKNOWN -> R.string.wear_own_presence_unknown
}

private fun WearLiveLimit.rowText(): Int = when (this) {
    WearLiveLimit.VIDEO -> R.string.wear_own_live_row_video
    WearLiveLimit.PROTECTED -> R.string.wear_own_live_row_protected
    WearLiveLimit.FORMAT -> R.string.wear_own_live_row_format
    WearLiveLimit.SEALED_FAILED -> R.string.wear_exchange_reason_sealed_failed
    WearLiveLimit.E2E_MISSING -> R.string.wear_exchange_reason_e2e_missing
}

private fun OwnNotice.text(): Int = when (this) {
    OwnNotice.ENDED -> R.string.wear_own_notice_ended
    OwnNotice.UNCONFIRMED -> R.string.wear_own_notice_unconfirmed
    OwnNotice.UNSUPPORTED_VIDEO -> R.string.wear_own_notice_video
    OwnNotice.UNSUPPORTED_PROTECTED -> R.string.wear_own_notice_protected
    OwnNotice.UNSUPPORTED_FORMAT -> R.string.wear_own_notice_format
    OwnNotice.OPEN_FAILED -> R.string.wear_own_notice_open_failed
    OwnNotice.SAVED -> R.string.wear_own_notice_saved
    OwnNotice.SAVE_FAILED -> R.string.wear_own_notice_save_failed
    OwnNotice.NEEDS_SERVER -> R.string.wear_own_notice_needs_server
    OwnNotice.SIGN_IN_REQUIRED -> R.string.wear_own_notice_sign_in
    OwnNotice.SEALED_FAILED -> R.string.wear_exchange_reason_sealed_failed
    OwnNotice.E2E_MISSING -> R.string.wear_exchange_reason_e2e_missing
}
