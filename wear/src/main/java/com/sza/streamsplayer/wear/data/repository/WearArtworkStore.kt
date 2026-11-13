package com.sza.streamsplayer.wear.data.repository

import android.content.Context
import com.sza.streamsplayer.wear.domain.delivery.DeliverableSet
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject
import timber.log.Timber
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S1154/S1201: access layer for the two on-demand artwork payloads on the watch, in the same
 * `filesDir/delivery/<set>/` layout as the phone. [WearArtworkDelivery] fills it; this store resolves
 * and parses what is there, and owns the swap of a verified staging directory for the installed one.
 *
 * Coords are keyed by the stream `url` (the stable unique index), mirroring [WearFaviconAtlasStore].
 */
@Singleton
class WearArtworkStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val root: File get() = File(context.filesDir, "delivery")

    private fun dir(set: DeliverableSet): File = File(root, set.name)

    /** The directory a download stages into; it becomes the payload directory only through [promote]. */
    fun stagingDir(set: DeliverableSet): File = File(root, "${set.name}.tmp")

    /** The installed tile pack, or null when this set is not installed. */
    fun tilePackFile(set: DeliverableSet): File? =
        File(dir(set), packName(set)).takeIf { it.isFile }

    /** The stamp the installed payload was fetched against, or null when nothing usable is installed. */
    suspend fun installedStamp(set: DeliverableSet): String? = withContext(Dispatchers.IO) {
        val file = File(dir(set), STAMP_FILE)
        if (!file.isFile || tilePackFile(set) == null) return@withContext null
        try {
            file.readText(Charsets.UTF_8).trim().ifBlank { null }
        } catch (e: IOException) {
            // An unreadable stamp reads as no stamp: the next refresh fetches again, which repairs it.
            Timber.i(e, "Artwork stamp unreadable for %s", set)
            null
        }
    }

    /** Writes the stamp into the staging directory, so it is promoted with the payload it describes. */
    fun writeStamp(stagingDir: File, stamp: String) {
        File(stagingDir, STAMP_FILE).writeText(stamp, Charsets.UTF_8)
    }

    /**
     * The persisted `url -> tile_index` map. An absent or corrupt sidecar yields an empty map (logged
     * at info, not error - a missing sidecar is the expected not-yet-downloaded state).
     */
    suspend fun coords(set: DeliverableSet): Map<String, Int> = withContext(Dispatchers.IO) {
        val file = File(dir(set), coordsName(set))
        if (!file.isFile) return@withContext emptyMap()
        try {
            decodeCoords(file.readText(Charsets.UTF_8))
        } catch (e: IOException) {
            Timber.i(e, "Artwork coords sidecar unreadable for %s; treating as empty", set)
            emptyMap()
        } catch (e: JSONException) {
            Timber.i(e, "Artwork coords sidecar malformed for %s; treating as empty", set)
            emptyMap()
        }
    }

    /**
     * Replace the payload with the verified [stagingDir] without destroying the prior payload until the
     * new one is in place: move the old payload aside to a `.bak`, rename staging in, then drop the
     * backup. If the final rename fails, restore the backup so a failed swap keeps the previous working
     * payload instead of leaving nothing. Renames within `filesDir` are atomic.
     */
    fun promote(set: DeliverableSet, stagingDir: File): Boolean {
        val payloadDir = dir(set)
        payloadDir.parentFile?.mkdirs()
        val backup = File(payloadDir.parentFile, "${payloadDir.name}.bak")
        backup.deleteRecursively()

        val movedOldAside = payloadDir.exists() && payloadDir.renameTo(backup)
        // If the old payload existed but could not be moved aside, remove it in place (best effort).
        if (payloadDir.exists()) payloadDir.deleteRecursively()

        if (stagingDir.renameTo(payloadDir)) {
            backup.deleteRecursively()
            return true
        }
        // Promotion failed - restore the previous payload if we had set it aside.
        if (movedOldAside) backup.renameTo(payloadDir)
        return false
    }

    /**
     * S0064: removes the installed payload of [set]. The stamp lives inside the payload directory, so it
     * goes in the same call and can never outlive its payload (STREAM-BANK rule 12, the S1200 hazard).
     * Returns false when something could not be deleted. A set that is not installed is already gone: true.
     */
    fun delete(set: DeliverableSet): Boolean = dir(set).deleteRecursively()

    private fun decodeCoords(text: String): Map<String, Int> {
        if (text.isBlank()) return emptyMap()
        val obj = JSONObject(text)
        val out = HashMap<String, Int>(obj.length())
        val keys = obj.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            // Skip non-integer values defensively; a malformed entry must not poison the whole map.
            val value = obj.opt(key)
            val index = (value as? Number)?.toInt() ?: (value as? String)?.toIntOrNull()
            if (index != null) out[key] = index
        }
        return out
    }

    companion object {
        private const val STAMP_FILE = "installed-stamp.txt"

        fun packName(set: DeliverableSet): String = when (set) {
            DeliverableSet.CHANNEL_PREVIEW_ATLAS -> "channel-preview-tiles.zip"
            DeliverableSet.STREAM_LOGO_ATLAS -> "stream-logo-tiles.zip"
        }

        fun coordsName(set: DeliverableSet): String = when (set) {
            DeliverableSet.CHANNEL_PREVIEW_ATLAS -> "channel-preview-coords.json"
            DeliverableSet.STREAM_LOGO_ATLAS -> "stream-logo-coords.json"
        }
    }
}
