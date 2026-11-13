package com.sza.streamsplayer.ui.settings.artwork

import com.sza.streamsplayer.domain.delivery.ExtensionStatus

/** What the row's first button asks for; [NONE] while nothing fits or a download is running. */
enum class ChannelPictureAction { NONE, DOWNLOAD, UPDATE, RETRY }

/** The sentence the row's status line shows. */
enum class ChannelPictureStatus { CHECKING, NOT_INSTALLED, INSTALLED, UPDATE_READY, DOWNLOADING, FAILED }

/**
 * One row of the channel-pictures group (S0064): the status line, the progress, and which buttons fit.
 *
 * Remove is offered whenever a copy may be on disk. A failed row keeps it: a failed update leaves the
 * old copy in place and the status cannot tell that apart from a failed first download, so hiding Remove
 * there would strand the space. Removing from a row with nothing on disk changes nothing but the status.
 */
data class ChannelPictureRowState(
    val status: ChannelPictureStatus,
    val percent: Int,
    val action: ChannelPictureAction,
    val canRemove: Boolean,
) {
    companion object {
        /** The row before its first status arrives: the page is drawn whole and no wrong button is offered. */
        val Checking = ChannelPictureRowState(ChannelPictureStatus.CHECKING, 0, ChannelPictureAction.NONE, false)

        fun of(status: ExtensionStatus): ChannelPictureRowState = when (status) {
            ExtensionStatus.NotInstalled ->
                ChannelPictureRowState(ChannelPictureStatus.NOT_INSTALLED, 0, ChannelPictureAction.DOWNLOAD, false)
            ExtensionStatus.Installed ->
                ChannelPictureRowState(ChannelPictureStatus.INSTALLED, 0, ChannelPictureAction.NONE, true)
            ExtensionStatus.UpdateAvailable ->
                ChannelPictureRowState(ChannelPictureStatus.UPDATE_READY, 0, ChannelPictureAction.UPDATE, true)
            is ExtensionStatus.Downloading ->
                ChannelPictureRowState(
                    ChannelPictureStatus.DOWNLOADING,
                    status.percent.coerceIn(0, MAX_PERCENT),
                    ChannelPictureAction.NONE,
                    false,
                )
            is ExtensionStatus.Failed ->
                ChannelPictureRowState(ChannelPictureStatus.FAILED, 0, ChannelPictureAction.RETRY, true)
        }

        private const val MAX_PERCENT = 100
    }
}

private const val BYTES_PER_MB = 1024L * 1024L

/**
 * The published size in whole megabytes, rounded up as the offer dialog does, or null when the manifest
 * gave none. Never zero: a size that rounds to nothing is no size.
 */
internal fun publishedMegabytes(bytes: Long?): Int? =
    bytes?.let { (it + BYTES_PER_MB - 1) / BYTES_PER_MB }?.takeIf { it > 0L }?.toInt()
