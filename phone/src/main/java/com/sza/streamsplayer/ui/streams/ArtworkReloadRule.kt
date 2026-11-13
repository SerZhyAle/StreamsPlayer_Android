package com.sza.streamsplayer.ui.streams

/**
 * Whether the list's artwork index disagrees with the disk and has to be read again.
 *
 * A payload that landed while the screen sat in the background (S1154) and one removed from Settings
 * (S0064) are the same mismatch seen from its two sides: the index holds entries for a payload that is
 * gone, or holds none for a payload that is there. Both are settled by one re-read.
 */
internal fun artworkIndexIsOutOfStep(indexLoaded: Boolean, payloadInstalled: Boolean): Boolean =
    indexLoaded != payloadInstalled
