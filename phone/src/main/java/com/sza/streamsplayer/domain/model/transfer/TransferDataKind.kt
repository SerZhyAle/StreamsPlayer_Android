package com.sza.streamsplayer.domain.model.transfer

/**
 * A data set the user can move between devices (S1565).
 * StreamsPlayer minimal subset for stream pins export/import.
 */
enum class TransferDataKind(
    val driveFileName: String,
    val mimeType: String,
    val formatVersion: Int
) {
    PINNED_STREAMS("streamsplayer_pinned_streams.json", MIME_JSON, PINNED_STREAMS_FORMAT_VERSION);

    companion object {
        const val DRIVE_FOLDER_NAME = "StreamsPlayer"
    }
}

private const val MIME_JSON = "application/json"
private const val PINNED_STREAMS_FORMAT_VERSION = 1
