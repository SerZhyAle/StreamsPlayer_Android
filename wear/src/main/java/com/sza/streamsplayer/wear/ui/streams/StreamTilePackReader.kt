package com.sza.streamsplayer.wear.ui.streams

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.util.zip.ZipFile

/**
 * S1445: reads one artwork tile out of a tile-pack container, in time that does not depend on how
 * many tiles the payload holds. Each tile is its own ZIP entry, so a tile costs one small image decode,
 * and a re-shown tile costs nothing.
 *
 * Container contract, shared with the offline packer: entry name is the slot index as a plain
 * decimal string with no extension, entries are stored uncompressed, and the image format of an
 * entry is whatever [BitmapFactory] can sniff.
 *
 * The [packFileProvider] is re-read on each (re)open, so [invalidate] after a download picks up the
 * new container.
 */
class StreamTilePackReader(
    private val packFileProvider: () -> File?,
) {
    private val mutex = Mutex()
    private var archive: ZipFile? = null
    private var opened = false

    // Sized in bytes rather than entries: a tile is a fixed-size bitmap per payload, but the two
    // payloads have different tile sizes and would otherwise need different entry counts.
    private val cache = object : LruCache<Int, Bitmap>(CACHE_BYTES) {
        override fun sizeOf(key: Int, value: Bitmap): Int = value.byteCount
    }

    /**
     * The tile bitmap for [index], or null when the pack is absent, holds no such entry, or the
     * entry does not decode. Runs off the main thread; a cache hit answers without touching disk.
     */
    suspend fun tile(index: Int): Bitmap? {
        if (index < 0) return null
        return cache.get(index) ?: withContext(Dispatchers.IO) { readTile(index) }
    }

    private suspend fun readTile(index: Int): Bitmap? {
        val zip = archive() ?: return null
        // archive() releases the mutex before returning, so invalidate() can close this handle at any
        // moment and every later access on it throws - entry lookup as much as the read. Keep both
        // inside this try (the S1220 lesson from the region-decoder path).
        return try {
            zip.getEntry(index.toString())
                ?.let { entry -> zip.getInputStream(entry).use { BitmapFactory.decodeStream(it) } }
                ?.also { cache.put(index, it) }
        } catch (e: IOException) {
            Timber.i(e, "Stream tile pack entry unreadable for index=$index")
            null
        } catch (e: CancellationException) {
            // A cancelled read is not a closed container: answering null here caches "no tile" for
            // an index that was never actually read (S1889).
            throw e
        } catch (e: IllegalStateException) {
            Timber.i(e, "Stream tile pack closed while reading index=$index")
            null
        }
    }

    /** Closes the container and drops every cached tile, so the next [tile] re-reads the provider. */
    suspend fun invalidate() = mutex.withLock { closeAndForget() }

    /** Does not wait for an active open: the screen is going away, and the reader goes with it. */
    fun releaseNow() {
        if (!mutex.tryLock()) return
        try {
            closeAndForget()
        } finally {
            mutex.unlock()
        }
    }

    private fun closeAndForget() {
        try {
            archive?.close()
        } catch (e: IOException) {
            Timber.i(e, "Stream tile pack close failed")
        }
        archive = null
        opened = false
        cache.evictAll()
    }

    private suspend fun archive(): ZipFile? = mutex.withLock {
        if (!opened) {
            opened = true
            val file = packFileProvider()
            archive = if (file != null && file.isFile) openArchive(file) else null
        }
        archive
    }

    private fun openArchive(file: File): ZipFile? = try {
        ZipFile(file)
    } catch (e: IOException) {
        Timber.i(e, "Stream tile pack could not open ${file.name}")
        null
    }

    private companion object {
        // Two readers live side by side (previews and logos). A watch screen shows a handful of cells,
        // and 4 MiB holds about 30 previews or 55 logos, so this is well above a screenful.
        const val CACHE_BYTES = 4 * 1024 * 1024
    }
}
