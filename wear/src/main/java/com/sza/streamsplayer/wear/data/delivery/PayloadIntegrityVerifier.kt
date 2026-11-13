package com.sza.streamsplayer.wear.data.delivery

import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Verifies a downloaded artwork file before it replaces anything: the size must sit between the file's
 * floor and the watch's ceiling, and a tile pack must pass its structural check (S1483).
 */
@Singleton
class PayloadIntegrityVerifier @Inject constructor() {

    sealed interface Result {
        data object Verified : Result
        data class Failed(val reason: String) : Result
    }

    fun verify(file: File, fileName: String, minSize: Long): Result {
        if (!file.isFile) {
            return fail(fileName, "payload file missing")
        }
        val size = file.length()
        if (size < minSize) {
            return fail(fileName, "size $size < minSize $minSize")
        }
        if (size > MAX_SET_BYTES) {
            return fail(fileName, "too large: $size > $MAX_SET_BYTES")
        }
        // S1483: without a hash the size bounds alone would accept a truncated download - which would
        // then overwrite working artwork with a file that decodes to nothing. Check the container
        // contract instead: the archive opens, it holds entries, and every entry name is the decimal
        // slot index StreamTilePackReader looks up.
        if (fileName.endsWith(".zip", ignoreCase = true)) {
            return verifyTilePack(file, fileName)
        }
        return Result.Verified
    }

    /**
     * S1483: structural verification of a tile pack. Every entry name must be a plain decimal slot
     * index - the container contract the reader and the offline packer share - so a zip of ordinary
     * files, an HTML error page saved as `.zip`, or a truncated download is rejected here rather than
     * discovered later as blank cells in the grid.
     */
    private fun verifyTilePack(file: File, fileName: String): Result = try {
        java.util.zip.ZipFile(file).use { zip ->
            val entries = zip.entries().toList()
            when {
                entries.isEmpty() -> fail(fileName, "tile pack has no entries")
                entries.any { it.name.toIntOrNull() == null } ->
                    fail(fileName, "tile pack entry name is not a slot index")
                else -> Result.Verified
            }
        }
    } catch (e: java.io.IOException) {
        // A truncated or non-zip payload throws ZipException/IOException here; that is precisely the
        // case this check exists for, so it is a verdict rather than an error to propagate.
        fail(fileName, "tile pack unreadable: ${e.message}")
    }

    private fun fail(fileName: String, reason: String): Result.Failed {
        Timber.w("Payload integrity check failed for %s: %s", fileName, reason)
        return Result.Failed("$fileName: $reason")
    }

    companion object {
        /**
         * STREAM-BANK rule 8, the watch's own ceiling for one artwork set. Measured 2026-11-08 off the
         * published manifest: 14.8 MB for the previews and 10.2 MB for the logos, pack and sidecar
         * together, so this is twice the largest set; a watch has far less room than the phone's 64 MiB.
         */
        const val MAX_SET_BYTES = 32L * 1024 * 1024
    }
}
