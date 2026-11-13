package com.sza.streamsplayer.widget

import android.content.Context
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StreamLaunchWidgetStoreTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun `save and read configuration returns saved values`() {
        val appWidgetId = 42
        val url = "https://example.com/radio.mp3"
        val title = "Radio Paradise"
        val mediaKind = "AUDIO"
        val bitmap = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)

        StreamLaunchWidgetStore.save(context, appWidgetId, url, title, mediaKind, bitmap)

        val config = StreamLaunchWidgetStore.read(context, appWidgetId)
        assertNotNull(config)
        assertEquals(url, config?.url)
        assertEquals(title, config?.title)
        assertEquals(mediaKind, config?.mediaKind)

        val savedIcon = StreamLaunchWidgetStore.readIcon(context, appWidgetId)
        assertNotNull(savedIcon)
        assertEquals(32, savedIcon?.width)
        assertEquals(32, savedIcon?.height)
    }

    @Test
    fun `delete removes both shared preferences and icon file`() {
        val appWidgetId = 43
        val bitmap = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
        StreamLaunchWidgetStore.save(context, appWidgetId, "https://test.com", "Test", "AUDIO", bitmap)

        assertTrue(StreamLaunchWidgetStore.iconFile(context, appWidgetId).exists())

        StreamLaunchWidgetStore.delete(context, appWidgetId)

        assertNull(StreamLaunchWidgetStore.read(context, appWidgetId))
        assertNull(StreamLaunchWidgetStore.readIcon(context, appWidgetId))
    }

    @Test
    fun `read unconfigured widget returns null`() {
        assertNull(StreamLaunchWidgetStore.read(context, 9999))
        assertNull(StreamLaunchWidgetStore.readIcon(context, 9999))
    }
}
