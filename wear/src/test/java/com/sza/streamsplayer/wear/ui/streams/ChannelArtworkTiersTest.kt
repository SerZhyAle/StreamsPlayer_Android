package com.sza.streamsplayer.wear.ui.streams

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** STREAM-BANK document 09 section 5 on the watch: the render order, and that a miss falls to the next tier. */
class ChannelArtworkTiersTest {

    private suspend fun resolve(
        mediaKind: String,
        preview: String? = "preview",
        logo: String? = "logo",
        favicon: String? = "favicon",
    ) = resolveChannelArtwork(mediaKind, "https://a/b", { preview }, { logo }, { favicon })

    @Test
    fun `a video channel prefers its preview`() = runTest {
        assertEquals("preview", resolve("VIDEO"))
    }

    @Test
    fun `a video channel with no preview falls to its logo, then its favicon`() = runTest {
        assertEquals("logo", resolve("VIDEO", preview = null))
        assertEquals("favicon", resolve("VIDEO", preview = null, logo = null))
    }

    @Test
    fun `radio never asks the preview tier`() = runTest {
        var asked = false
        val artwork = resolveChannelArtwork(
            mediaKind = "AUDIO",
            url = "https://a/b",
            preview = {
                asked = true
                "preview"
            },
            logo = { "logo" },
            favicon = { "favicon" },
        )
        assertFalse(asked)
        assertEquals("logo", artwork)
    }

    @Test
    fun `an rtsp address has no preview tier even when marked as video`() {
        assertFalse(usesPreviewTier("VIDEO", "rtsp://a/b"))
        assertTrue(usesPreviewTier("VIDEO", "https://a/b"))
    }

    @Test
    fun `a watch with none of the tiers leaves the glyph`() = runTest {
        assertNull(resolve("VIDEO", preview = null, logo = null, favicon = null))
    }
}
