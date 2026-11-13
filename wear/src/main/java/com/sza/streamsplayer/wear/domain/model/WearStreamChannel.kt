package com.sza.streamsplayer.wear.domain.model

import com.google.gson.annotations.SerializedName

/**
 * S1708: Domain model representing a streaming channel (radio or video) on Wear OS.
 */
data class WearStreamChannel(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("url") val url: String,
    @SerializedName("media_kind") val mediaKind: String, // "AUDIO" | "VIDEO" | "RTSP"
    @SerializedName("favicon_index") val faviconIndex: Int? = null,
    @SerializedName("category") val category: String? = null,
    @SerializedName("topic") val topic: String? = null,
    @SerializedName("language") val language: String? = null,
    @SerializedName("country") val country: String? = null,
    @SerializedName("access") val access: String? = null,
    // S1799: null = catalog row (every pre-existing stored file reads as null); ORIGIN_PHONE marks a
    // row transferred from the phone. Gson fills absent JSON fields with null regardless of Kotlin
    // defaults, so null-means-catalog is the only shape already-shipped channels.json files satisfy.
    @SerializedName("origin") val origin: String? = null,
    // S0038: what a channel kept from a broadcast is the saved side of. `broadcastSourceId` is the descriptor's
    // opaque `sourceId` (LIVE-BROADCAST 3.1); the entry and broadcast ids bind it to the account it was kept
    // from on this watch. All null for every other channel and for every channels.json written before.
    @SerializedName("broadcast_source_id") val broadcastSourceId: String? = null,
    @SerializedName("exchange_entry_id") val exchangeEntryId: String? = null,
    @SerializedName("exchange_broadcast_id") val exchangeBroadcastId: String? = null
) {
    companion object {
        /**
         * A personal channel: transferred from the phone, or (S0038) a broadcast kept on this watch. The stored
         * value predates the second case and stays, because every rule for it - kept across a catalog refresh,
         * listed under Own, outside the audio and video kinds - holds for both.
         */
        const val ORIGIN_PHONE = "PHONE"
    }
}

fun WearStreamChannel.toFavoriteRecord(): WearFavoriteRecord = WearFavoriteRecord(
    sourceId = SOURCE_ID_STREAM,
    filePath = normalizeWearStreamUrl(url),
    displayName = name,
    mimeType = if (mediaKind.equals("VIDEO", ignoreCase = true) || mediaKind.equals("RTSP", ignoreCase = true)) {
        "video/*"
    } else {
        "audio/*"
    },
    itemKind = FAVORITE_ITEM_KIND_STREAM
)
