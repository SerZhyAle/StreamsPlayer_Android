package com.sza.streamsplayer.ui.streams

/** The on-demand and bundled picture tiers of a channel, best first (STREAM-BANK document 09 section 5). */
enum class ChannelArtworkTier { PREVIEW, LOGO, FAVICON }

/** One channel's resolved picture and the tier that supplied it. */
data class ChannelArtwork<out T : Any>(val tier: ChannelArtworkTier, val picture: T)

/** Only an http(s) VIDEO channel has a frame a preview tile could show; every other kind skips that tier. */
internal fun usesPreviewTier(mediaKind: String, url: String): Boolean =
    mediaKind.equals("VIDEO", ignoreCase = true) &&
        (url.startsWith("http://") || url.startsWith("https://"))

/**
 * Walks the tiers in the contract's order, each miss falling through to the next. Null means the caller
 * keeps the media-kind glyph, so an install with neither on-demand payload renders from the favicon
 * exactly as it does without this code.
 */
internal suspend fun <T : Any> resolveChannelArtwork(
    mediaKind: String,
    url: String,
    preview: suspend () -> T?,
    logo: suspend () -> T?,
    favicon: suspend () -> T?,
): ChannelArtwork<T>? {
    if (usesPreviewTier(mediaKind, url)) {
        preview()?.let { return ChannelArtwork(ChannelArtworkTier.PREVIEW, it) }
    }
    logo()?.let { return ChannelArtwork(ChannelArtworkTier.LOGO, it) }
    favicon()?.let { return ChannelArtwork(ChannelArtworkTier.FAVICON, it) }
    return null
}
