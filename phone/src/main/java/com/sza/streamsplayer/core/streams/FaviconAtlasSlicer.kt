package com.sza.streamsplayer.core.streams

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.util.LruCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File

/**
 * Turns `(atlasFile, faviconIndex)` into a 32 px tile [Bitmap] using the fixed-grid
 * contract shared with the offline packer.
 */
class FaviconAtlasSlicer(
    private val atlasFileProvider: () -> File?,
) {

    /** The pixel rect of tile [index] in the fixed grid. */
    fun rectFor(index: Int): Rect = rectOf(index)

    /** True only when [index] is non-negative and its tile rect fits inside `atlasWidth x atlasHeight`. */
    fun isInBounds(index: Int, atlasWidth: Int, atlasHeight: Int): Boolean =
        inBoundsOf(index, atlasWidth, atlasHeight)

    /**
     * The 32 px tile bitmap for [index], or null when the atlas is absent or the index is out of bounds.
     */
    suspend fun tileFor(index: Int): Bitmap? = withContext(Dispatchers.IO) {
        SharedFaviconAtlas.tileFor(atlasFileProvider, index)
    }

    /** Drops the shared sheet and every cached tile. */
    suspend fun invalidate() = SharedFaviconAtlas.invalidate()

    companion object {
        const val TILE = 32
        const val COLS = 16

        internal fun rectOf(index: Int): Rect {
            val col = index % COLS
            val row = index / COLS
            val left = col * TILE
            val top = row * TILE
            return Rect(left, top, left + TILE, top + TILE)
        }

        internal fun inBoundsOf(index: Int, atlasWidth: Int, atlasHeight: Int): Boolean {
            return index >= 0 &&
                index % COLS < atlasWidth / TILE &&
                index / COLS < atlasHeight / TILE
        }
    }
}

private object SharedFaviconAtlas {
    private const val TILE_CACHE_BYTES = 1024 * 1024
    const val SHEET_IDLE_RELEASE_MS = 5_000L

    private val mutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val tiles = object : LruCache<String, Bitmap>(TILE_CACHE_BYTES) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    private var sheet: Bitmap? = null
    private var sheetKey: String? = null
    private var releaseJob: Job? = null

    suspend fun tileFor(atlasFileProvider: () -> File?, index: Int): Bitmap? = mutex.withLock {
        val file = atlasFileProvider()?.takeIf { it.isFile }
        when {
            index < 0 || file == null -> null
            else -> {
                val key = identityOf(file)
                tiles.get(tileKey(key, index)) ?: cutTile(file, key, index)
            }
        }
    }

    suspend fun invalidate() = mutex.withLock {
        releaseJob?.cancel()
        releaseJob = null
        releaseSheet()
        tiles.evictAll()
    }

    private fun cutTile(file: File, identity: String, index: Int): Bitmap? {
        val atlas = sheetFor(file, identity)
        val tile = when {
            atlas == null || !inBoundsOfAtlas(atlas, index) -> null
            else -> cropOrNull(atlas, index)
        }
        if (tile != null) {
            tiles.put(tileKey(identity, index), tile)
        }
        scheduleSheetRelease()
        return tile
    }

    private fun cropOrNull(atlas: Bitmap, index: Int): Bitmap? {
        val rect = FaviconAtlasSlicer.rectOf(index)
        return try {
            Bitmap.createBitmap(atlas, rect.left, rect.top, FaviconAtlasSlicer.TILE, FaviconAtlasSlicer.TILE)
        } catch (e: IllegalArgumentException) {
            Timber.i(e, "Favicon atlas region rejected for index=%d", index)
            null
        }
    }

    private fun sheetFor(file: File, identity: String): Bitmap? {
        val cached = sheet
        return if (cached != null && !cached.isRecycled && sheetKey == identity) {
            cached
        } else {
            releaseSheet()
            sheetKey = identity
            sheet = BitmapFactory.decodeFile(file.path)
            sheet
        }
    }

    private fun scheduleSheetRelease() {
        releaseJob?.cancel()
        releaseJob = scope.launch {
            delay(SHEET_IDLE_RELEASE_MS)
            mutex.withLock { releaseSheet() }
        }
    }

    private fun releaseSheet() {
        sheet?.recycle()
        sheet = null
        sheetKey = null
    }

    private fun inBoundsOfAtlas(atlas: Bitmap, index: Int): Boolean =
        FaviconAtlasSlicer.inBoundsOf(index, atlas.width, atlas.height)

    private fun identityOf(file: File): String = "${file.path}|${file.lastModified()}|${file.length()}"

    private fun tileKey(identity: String, index: Int): String = "$identity#$index"
}
