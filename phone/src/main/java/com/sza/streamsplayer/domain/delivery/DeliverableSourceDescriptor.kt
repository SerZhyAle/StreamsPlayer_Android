package com.sza.streamsplayer.domain.delivery

/**
 * Describes the on-demand payload of one [DeliverableSet]: which files to fetch, where from (in
 * priority order), and the integrity anchors used to verify them before attach
 * (S0386 strategic §6.1 B1/B2). Pure data - no Android dependencies.
 *
 * Integrity (B2): [PayloadFile.sha256] and [PayloadFile.minSize] are compiled into the APK. A blank
 * [PayloadFile.sha256] marks an unverified pure-resource file, which still enforces
 * [PayloadFile.minSize] and the structural check of a tile pack.
 */
data class DeliverableSourceDescriptor(
    val set: DeliverableSet,
    val files: List<PayloadFile>
) {
    /**
     * S1200: the payload's identity when the published manifest gave no stamp at install time.
     *
     * Sorted by file name so a reordered [files] list cannot read as a different payload. A file with
     * no hash (the unverified pure-resource case) contributes its size instead, otherwise two such
     * files would collide on an empty string.
     */
    val stamp: String
        get() = files
            .sortedBy { it.fileName }
            .joinToString(";") { file ->
                val identity = file.sha256.ifBlank { "size:${file.minSize}" }
                "${file.fileName}=$identity"
            }
}

/**
 * One downloadable file inside a set's payload.
 *
 * @property fileName destination name under `filesDir/delivery/<set>/`.
 * @property sources ordered candidate URLs, tried first-to-last until one responds and verifies.
 * @property sha256 app-pinned lowercase hex SHA-256; blank for unverified pure-resource files.
 * @property minSize lower size bound (bytes) guarding against truncated/empty downloads.
 */
data class PayloadFile(
    val fileName: String,
    val sources: List<String>,
    val sha256: String,
    val minSize: Long
)
