package com.sza.streamsplayer.wear.domain.delivery

/**
 * S1483: what the mirror currently serves for the artwork payloads.
 *
 * The artwork sets are published under stable names, so an installed copy cannot tell a rebuild
 * happened by looking at a URL. This manifest is the freshness signal instead: it is published by the
 * same run that uploads the payload, so the two cannot disagree.
 *
 * Absence is not an error. A manifest that cannot be fetched or parsed means "nothing new".
 */
data class ArtworkManifest(
    val schemaVersion: Int,
    val generatedAt: String,
    val sets: Map<DeliverableSet, ArtworkManifestSet>,
) {
    companion object {
        /** The only schema this build understands; a newer one is ignored rather than guessed at. */
        const val SUPPORTED_SCHEMA_VERSION = 1

        /** Manifest member names, fixed by the publisher (`collect-stream-candidates.ps1`). */
        const val SET_CHANNEL_PREVIEW = "channelPreview"
        const val SET_STREAM_LOGO = "streamLogo"
    }
}

/**
 * One payload's live state. [stamp] is the tile pack's own SHA-256: it changes exactly when the
 * artwork changes, which is the only event a client needs to notice. [totalBytes] is compared with the
 * watch's ceiling before a download starts.
 */
data class ArtworkManifestSet(
    val stamp: String,
    val totalBytes: Long,
)
