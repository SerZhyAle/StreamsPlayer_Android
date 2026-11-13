package com.sza.streamsplayer.wear.ui.pictures

import com.sza.streamsplayer.wear.domain.delivery.DeliverableSet

/** What the row's download button asks for; [NONE] while nothing fits or a download is running. */
enum class WearChannelPictureAction { NONE, DOWNLOAD, RETRY }

/** The sentence the row's status line shows. */
enum class WearChannelPictureStatus { CHECKING, NOT_INSTALLED, INSTALLED, DOWNLOADING, FAILED }

/**
 * One row of the watch's Channel pictures screen (S0064): the status line and which buttons fit.
 *
 * The watch has no "newer set ready" state: its refresh replaces a set whose stamp changed by itself,
 * so a set is either held or not, and the wearer's only choices are Download and Remove. Remove is
 * offered only for a set that is held, because the watch has no copy that a failed download could have
 * left behind (the delivery promotes all-or-nothing and keeps what it held).
 */
data class WearChannelPictureRowState(
    val status: WearChannelPictureStatus,
    val action: WearChannelPictureAction,
    val canRemove: Boolean,
) {
    companion object {
        /** The row before the disk was read: the screen is drawn whole and no wrong button is offered. */
        val Checking = WearChannelPictureRowState(WearChannelPictureStatus.CHECKING, WearChannelPictureAction.NONE, false)

        fun of(installed: Boolean, downloading: Boolean, failed: Boolean): WearChannelPictureRowState = when {
            downloading ->
                WearChannelPictureRowState(WearChannelPictureStatus.DOWNLOADING, WearChannelPictureAction.NONE, false)
            installed ->
                WearChannelPictureRowState(WearChannelPictureStatus.INSTALLED, WearChannelPictureAction.NONE, true)
            failed ->
                WearChannelPictureRowState(WearChannelPictureStatus.FAILED, WearChannelPictureAction.RETRY, false)
            else ->
                WearChannelPictureRowState(WearChannelPictureStatus.NOT_INSTALLED, WearChannelPictureAction.DOWNLOAD, false)
        }
    }
}

/**
 * Everything the screen knows. [installed] is null until the disk was read, which keeps the first frame
 * from offering a button that fits no state; [megabytes] arrives later and only ever adds a line.
 */
data class WearChannelPicturesState(
    val installed: Map<DeliverableSet, Boolean>? = null,
    val busy: Set<DeliverableSet> = emptySet(),
    val failed: Set<DeliverableSet> = emptySet(),
    val megabytes: Map<DeliverableSet, Int> = emptyMap(),
) {
    fun rowOf(set: DeliverableSet): WearChannelPictureRowState {
        val held = installed ?: return WearChannelPictureRowState.Checking
        return WearChannelPictureRowState.of(
            installed = held[set] == true,
            downloading = set in busy,
            failed = set in failed,
        )
    }
}

/** The two sets in the order the screen lists them: previews first, as the phone's General page does. */
internal val WEAR_PICTURE_SETS = listOf(DeliverableSet.CHANNEL_PREVIEW_ATLAS, DeliverableSet.STREAM_LOGO_ATLAS)

private const val BYTES_PER_MB = 1024L * 1024L

/**
 * The published size in whole megabytes, rounded up as the phone's offer dialog does, or null when the
 * manifest gave none. Never zero: a size that rounds to nothing is no size.
 */
internal fun publishedMegabytes(bytes: Long?): Int? =
    bytes?.let { (it + BYTES_PER_MB - 1) / BYTES_PER_MB }?.takeIf { it > 0L }?.toInt()
