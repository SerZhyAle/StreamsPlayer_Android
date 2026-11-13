package com.sza.streamsplayer.data.delivery

import com.sza.streamsplayer.domain.delivery.DeliverableSet
import com.sza.streamsplayer.domain.delivery.DeliverableSourceDescriptor
import com.sza.streamsplayer.domain.delivery.PayloadFile

/**
 * App-pinned download descriptors for the deliverable sets (S0386 Phase 05).
 *
 * URLs point at the delivery mirror release. The remote manifest may override only the URLs at
 * runtime ([DeliveryManifestDataSource]), never the size floors compiled here.
 */
object DeliverableDescriptorCatalog {

    private const val MIRROR =
        "https://github.com/SerZhyAle/FastMediaSorter_mob_v2/releases/download/delivery-so-v1"

    // S1483: the artwork payloads are NOT pinned by hash and carry no revision in their names.
    //
    // A tile pack is pictures: the worst a substituted one can do is show the wrong image, and the
    // catalog itself - the file that decides which URLs the app will play - has never been pinned
    // either. Weighing a pin nobody can update against a rebuild reaching users the same day, the pin
    // loses.
    //
    // What replaces it: a size floor here (a truncated download cannot pass it), a structural check at
    // install time (the archive opens and every entry is a decimal slot index), and `artwork-manifest.json`
    // as the freshness signal, so the answer to "is there something newer" comes from the mirror
    // rather than from a constant compiled into this build.
    private const val ATLAS_PACK_MIN_SIZE = 1_000_000L
    private const val ATLAS_COORDS_MIN_SIZE = 32_768L

    /**
     * Channel-preview atlas (S1154) - on-demand stream channel-preview tile pack + `url->index`
     * sidecar. Pure data payload, contributed only on demand, never bundled.
     */
    fun channelPreviewAtlas(): DeliverableSourceDescriptor = DeliverableSourceDescriptor(
        set = DeliverableSet.CHANNEL_PREVIEW_ATLAS,
        files = listOf(
            resource("channel-preview-tiles.zip", ATLAS_PACK_MIN_SIZE),
            resource("channel-preview-coords.json", ATLAS_COORDS_MIN_SIZE)
        )
    )

    // Same S1483 policy as the preview payload above.
    private const val LOGO_PACK_MIN_SIZE = 1_000_000L
    private const val LOGO_COORDS_MIN_SIZE = 32_768L

    /**
     * Stream logo atlas (S1201) - on-demand station-logo tile pack + `url->index` sidecar. Same shape
     * as [channelPreviewAtlas]. It exists alongside rather than inside the preview atlas because a
     * station with no video track can never have a captured frame, and the two payloads are worth
     * refusing independently.
     */
    fun streamLogoAtlas(): DeliverableSourceDescriptor = DeliverableSourceDescriptor(
        set = DeliverableSet.STREAM_LOGO_ATLAS,
        files = listOf(
            resource("stream-logo-tiles.zip", LOGO_PACK_MIN_SIZE),
            resource("stream-logo-coords.json", LOGO_COORDS_MIN_SIZE)
        )
    )

    /** S1483: a payload verified by structure and size rather than by a hash frozen into this build. */
    private const val UNPINNED = ""

    /** S1483: published under a name with no revision, so a rebuild lands where installs already look. */
    private fun resource(name: String, minSize: Long): PayloadFile =
        PayloadFile(
            fileName = name,
            sources = listOf("$MIRROR/$name"),
            sha256 = UNPINNED,
            minSize = minSize
        )
}
