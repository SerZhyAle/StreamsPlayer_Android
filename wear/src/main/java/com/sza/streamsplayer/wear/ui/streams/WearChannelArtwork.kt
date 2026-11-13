package com.sza.streamsplayer.wear.ui.streams

import android.graphics.Bitmap
import com.sza.streamsplayer.wear.data.delivery.WearArtworkDelivery
import com.sza.streamsplayer.wear.data.repository.WearArtworkStore
import com.sza.streamsplayer.wear.domain.delivery.DeliverableSet
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject

/** Only an http(s) VIDEO channel has a frame a preview tile could show; every other kind skips that tier. */
internal fun usesPreviewTier(mediaKind: String, url: String): Boolean =
    mediaKind.equals("VIDEO", ignoreCase = true) &&
        (url.startsWith("http://") || url.startsWith("https://"))

/**
 * STREAM-BANK document 09 section 5 on the watch: preview, logo, favicon, each miss falling through to
 * the next. Null means the cell keeps its glyph, so a watch with neither payload renders from the
 * favicon exactly as it does without this code.
 */
internal suspend fun <T : Any> resolveChannelArtwork(
    mediaKind: String,
    url: String,
    preview: suspend () -> T?,
    logo: suspend () -> T?,
    favicon: suspend () -> T?,
): T? {
    if (usesPreviewTier(mediaKind, url)) {
        preview()?.let { return it }
    }
    return logo() ?: favicon()
}

/**
 * The watch's two on-demand tiers, owned by one streams screen and released with it. A stale or
 * oversized index, or a missing entry, yields no tile (STREAM-BANK rule 8): the reader finds no such
 * entry, and the cell falls through to the favicon.
 */
class WearChannelArtwork @Inject constructor(
    private val store: WearArtworkStore,
    private val delivery: WearArtworkDelivery,
) {
    private val previewPack = StreamTilePackReader { store.tilePackFile(DeliverableSet.CHANNEL_PREVIEW_ATLAS) }
    private val logoPack = StreamTilePackReader { store.tilePackFile(DeliverableSet.STREAM_LOGO_ATLAS) }

    private val coordsMutex = Mutex()
    private val coordsBySet = HashMap<DeliverableSet, Map<String, Int>>()

    suspend fun previewTile(url: String): Bitmap? =
        coords(DeliverableSet.CHANNEL_PREVIEW_ATLAS)[url]?.let { previewPack.tile(it) }

    suspend fun logoTile(url: String): Bitmap? =
        coords(DeliverableSet.STREAM_LOGO_ATLAS)[url]?.let { logoPack.tile(it) }

    /** Polls the manifest and fetches what changed; [onChanged] runs after each set that landed. */
    suspend fun refresh(onChanged: () -> Unit) {
        delivery.refresh { set ->
            coordsMutex.withLock { coordsBySet.remove(set) }
            when (set) {
                DeliverableSet.CHANNEL_PREVIEW_ATLAS -> previewPack.invalidate()
                DeliverableSet.STREAM_LOGO_ATLAS -> logoPack.invalidate()
            }
            onChanged()
        }
    }

    fun releaseNow() {
        previewPack.releaseNow()
        logoPack.releaseNow()
    }

    // The index is one parse per payload, and every visible cell asks for it.
    private suspend fun coords(set: DeliverableSet): Map<String, Int> = coordsMutex.withLock {
        coordsBySet.getOrPut(set) { store.coords(set) }
    }
}
