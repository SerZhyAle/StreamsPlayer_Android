package com.sza.streamsplayer.widget

import com.sza.streamsplayer.core.network.NetworkContextAnalyzer
import com.sza.streamsplayer.data.local.db.StreamSourceEntity
import com.sza.streamsplayer.domain.usecase.streams.GetStreamSourceByUrlUseCase
import com.sza.streamsplayer.ui.streams.helpers.StreamShortcutRouteManager
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StreamShortcutRouteManagerTest {

    private val getStreamSourceByUrl: GetStreamSourceByUrlUseCase = mockk()
    private val networkContextAnalyzer: NetworkContextAnalyzer = mockk()
    private val manager = StreamShortcutRouteManager(getStreamSourceByUrl, networkContextAnalyzer)

    @Test
    fun `headlessSource returns source when stream is AUDIO and network is available`() = runTest {
        val audioSource = StreamSourceEntity(
            id = "audio-1",
            url = "https://example.com/audio.mp3",
            title = "Radio",
            mediaKind = "AUDIO",
            sourceOrigin = "MANUAL",
            sortIndex = 0,
            addedAt = 1000L,
        )
        coEvery { getStreamSourceByUrl("https://example.com/audio.mp3") } returns audioSource
        coEvery { networkContextAnalyzer.hasAnyNetwork() } returns true

        val result = manager.headlessSource("https://example.com/audio.mp3")
        assertEquals(audioSource, result)
    }

    @Test
    fun `headlessSource returns null when stream is VIDEO`() = runTest {
        val videoSource = StreamSourceEntity(
            id = "video-1",
            url = "https://example.com/live.m3u8",
            title = "TV",
            mediaKind = "VIDEO",
            sourceOrigin = "MANUAL",
            sortIndex = 0,
            addedAt = 1000L,
        )
        coEvery { getStreamSourceByUrl("https://example.com/live.m3u8") } returns videoSource
        coEvery { networkContextAnalyzer.hasAnyNetwork() } returns true

        val result = manager.headlessSource("https://example.com/live.m3u8")
        assertNull(result)
    }

    @Test
    fun `headlessSource returns null when network is unavailable`() = runTest {
        val audioSource = StreamSourceEntity(
            id = "audio-1",
            url = "https://example.com/audio.mp3",
            title = "Radio",
            mediaKind = "AUDIO",
            sourceOrigin = "MANUAL",
            sortIndex = 0,
            addedAt = 1000L,
        )
        coEvery { getStreamSourceByUrl("https://example.com/audio.mp3") } returns audioSource
        coEvery { networkContextAnalyzer.hasAnyNetwork() } returns false

        val result = manager.headlessSource("https://example.com/audio.mp3")
        assertNull(result)
    }

    @Test
    fun `headlessSource returns null for unknown url`() = runTest {
        coEvery { getStreamSourceByUrl("https://example.com/unknown") } returns null
        val result = manager.headlessSource("https://example.com/unknown")
        assertNull(result)
    }
}
