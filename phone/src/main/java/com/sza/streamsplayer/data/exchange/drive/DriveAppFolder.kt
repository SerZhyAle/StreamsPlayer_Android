package com.sza.streamsplayer.data.exchange.drive

/**
 * The Drive channel of DEVICE-EXCHANGE sections 8 and 24 as this product uses it (S0029): the hidden
 * app folder of the user's own Google account, the one non-sensitive scope that reaches it, and the
 * working numbers of items C and CQ.
 *
 * The ttl and cadence values are the working defaults of the FMS_W proposal of 2026-10-23, which rung 3
 * of the contract has not frozen yet; they live here and nowhere else, so freezing or moving them is one edit.
 */
object DriveAppFolder {

    /** Item CM: the only scope the channel ever requests. */
    const val SCOPE = "https://www.googleapis.com/auth/drive.appdata"

    /** What an authorization request of this product names - exactly the app folder, nothing wider. */
    val REQUESTED_SCOPES: List<String> = listOf(SCOPE)

    /** The Drive `spaces` value and the parent alias of the hidden app folder (item CM). */
    const val SPACE = "appDataFolder"

    /** Item C working default: a broadcast record is read as ended 90 s after its last write. */
    const val BROADCAST_TTL_SECONDS = 90L

    /** Item C working default for the device record that names the announcing device. */
    const val DEVICE_TTL_SECONDS = 300L

    /** Item C: a record that claims a lifetime outside this range is read with the default of its kind. */
    const val MIN_BELIEVED_TTL_SECONDS = 1L
    const val MAX_BELIEVED_TTL_SECONDS = 86_400L

    /** Section 8.1: one record, one file. */
    const val RECORD_PREFIX = "fmsx-"
    const val BROADCAST_PREFIX = "fmsx-broadcast-"
    const val DEVICE_PREFIX = "fmsx-device-"

    /** Item CQ: how often a reader lists while the screen that shows the announcements is open. */
    const val LIST_POLL_MS = 10_000L

    /** Item CQ back-off: from 1 s, doubling, to at most 5 min, with jitter. */
    const val BACKOFF_FIRST_MS = 1_000L
    const val BACKOFF_CAP_MS = 5 * 60_000L

    /** Bound on the files one poll reads, so a folder full of records cannot turn a poll into a download. */
    const val MAX_FILES_READ_PER_KIND = 50

    /** Item C: a standing record is rewritten at least every `ttlSeconds / 3`. */
    fun heartbeatMs(ttlSeconds: Long): Long = ttlSeconds * 1_000L / 3

    /** The lifetime a reader believes: the record's own claim when sane, else the default of its kind. */
    fun believedTtlSeconds(claimed: Long, defaultSeconds: Long): Long =
        if (claimed in MIN_BELIEVED_TTL_SECONDS..MAX_BELIEVED_TTL_SECONDS) claimed else defaultSeconds

    /**
     * Item CQ back-off after [failures] consecutive rate-limit or server errors, jittered into
     * [0.5, 1.0] of itself and never above [BACKOFF_CAP_MS].
     */
    fun backoffMs(failures: Int, jitter: Double): Long {
        val steps = (failures - 1).coerceIn(0, 20)
        val raw = (BACKOFF_FIRST_MS shl steps).coerceAtMost(BACKOFF_CAP_MS)
        return (raw * (0.5 + 0.5 * jitter.coerceIn(0.0, 1.0))).toLong().coerceAtMost(BACKOFF_CAP_MS)
    }
}
