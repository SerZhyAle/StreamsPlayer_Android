package com.sza.streamsplayer.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.mockk.mockk
import io.mockk.verify
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StreamLaunchWidgetProviderTest {

    @Test
    fun `updateAppWidget updates unconfigured widget cleanly`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appWidgetManager = mockk<AppWidgetManager>(relaxed = true)

        StreamLaunchWidgetProvider.updateAppWidget(context, appWidgetManager, 201)

        verify { appWidgetManager.updateAppWidget(201, any()) }
    }

    @Test
    fun `updateAppWidget updates configured widget cleanly`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appWidgetManager = mockk<AppWidgetManager>(relaxed = true)

        StreamLaunchWidgetStore.save(
            context = context,
            appWidgetId = 202,
            url = "https://example.com/radio.mp3",
            title = "My Radio",
            mediaKind = "AUDIO",
            iconTile = null,
        )

        StreamLaunchWidgetProvider.updateAppWidget(context, appWidgetManager, 202)

        verify { appWidgetManager.updateAppWidget(202, any()) }
    }
}
