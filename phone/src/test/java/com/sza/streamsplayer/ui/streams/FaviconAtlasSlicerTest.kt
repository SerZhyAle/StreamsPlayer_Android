package com.sza.streamsplayer.ui.streams

import com.sza.streamsplayer.core.streams.FaviconAtlasSlicer
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FaviconAtlasSlicerTest {

    private val slicer = FaviconAtlasSlicer { fixtureAtlasFile() }

    @Test
    fun `rectFor matches the 32px 16-col contract`() {
        assertEquals(android.graphics.Rect(0, 0, 32, 32), slicer.rectFor(0))
        assertEquals(android.graphics.Rect(32, 0, 64, 32), slicer.rectFor(1))
        assertEquals(android.graphics.Rect(0, 32, 32, 64), slicer.rectFor(16))
        assertEquals(android.graphics.Rect(32, 32, 64, 64), slicer.rectFor(17))
    }

    @Test
    fun `isInBounds rejects negative and over-range indices`() {
        assertFalse(slicer.isInBounds(-1, 64, 64))
        assertFalse(slicer.isInBounds(3, 64, 64))
        assertTrue(slicer.isInBounds(17, 64, 64))
        assertTrue(slicer.isInBounds(0, 64, 64))
        assertFalse(slicer.isInBounds(Int.MAX_VALUE, 512, 512))
        assertFalse(slicer.isInBounds(1_073_741_824, 512, 512))
        assertFalse(slicer.isInBounds(0, 31, 32))
        assertFalse(slicer.isInBounds(0, 32, -32))
    }

    @Test
    fun `tileFor returns a 32x32 tile whose centre matches the fixture colour`() = runTest {
        val tile0 = slicer.tileFor(0)
        assertNotNull("index 0 must decode", tile0)
        assertEquals(32, tile0!!.width)
        assertEquals(32, tile0.height)
        assertColorClose(0xFFDC2828.toInt(), tile0.getPixel(16, 16))

        val tile17 = slicer.tileFor(17)
        assertNotNull(tile17)
        assertColorClose(0xFFF0D21E.toInt(), tile17!!.getPixel(16, 16))
    }

    @Test
    fun `tileFor returns null for an out-of-bounds index`() = runTest {
        assertNull(slicer.tileFor(3))
        assertNull(slicer.tileFor(-1))
    }

    @Test
    fun `tileFor returns null when the atlas file is absent`() = runTest {
        val noAtlas = FaviconAtlasSlicer { null }
        assertNull(noAtlas.tileFor(0))
    }

    @Test
    fun `a second slicer over the same atlas gets the shared tile, not a second decode`() = runTest {
        val atlas = fixtureAtlasFile()
        val firstOwner = FaviconAtlasSlicer { atlas }
        val secondOwner = FaviconAtlasSlicer { atlas }

        val fromFirst = firstOwner.tileFor(0)
        val fromSecond = secondOwner.tileFor(0)

        assertNotNull("index 0 must decode", fromFirst)
        assertSame(fromFirst, fromSecond)
    }

    private fun assertColorClose(expected: Int, actual: Int) {
        val er = (expected shr 16) and 0xFF
        val eg = (expected shr 8) and 0xFF
        val eb = expected and 0xFF
        val ar = (actual shr 16) and 0xFF
        val ag = (actual shr 8) and 0xFF
        val ab = actual and 0xFF
        val close = Math.abs(er - ar) <= 8 && Math.abs(eg - ag) <= 8 && Math.abs(eb - ab) <= 8
        assertTrue(
            "colour mismatch: expected ~#%08X but was #%08X".format(expected, actual),
            close,
        )
    }

    private fun fixtureAtlasFile(): File {
        val stream = javaClass.classLoader!!.getResourceAsStream("streams/favicon-atlas.png")
            ?: error("fixture favicon-atlas.png not found on the test classpath")
        val tmp = File.createTempFile("favicon-atlas", ".png")
        stream.use { input -> tmp.outputStream().use { input.copyTo(it) } }
        tmp.deleteOnExit()
        return tmp
    }
}
