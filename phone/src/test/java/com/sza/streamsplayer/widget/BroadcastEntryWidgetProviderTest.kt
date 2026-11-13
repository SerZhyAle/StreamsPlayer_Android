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
class BroadcastEntryWidgetProviderTest {

    @Test
    fun `updateAppWidget builds remote views and calls updateAppWidget`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appWidgetManager = mockk<AppWidgetManager>(relaxed = true)

        BroadcastEntryWidgetProvider.updateAppWidget(context, appWidgetManager, 101)

        verify { appWidgetManager.updateAppWidget(101, any()) }
    }

    @Test
    fun `onUpdate invokes updateAppWidget for each id`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appWidgetManager = mockk<AppWidgetManager>(relaxed = true)
        val provider = BroadcastEntryWidgetProvider()

        provider.onUpdate(context, appWidgetManager, intArrayOf(101, 102))

        verify { appWidgetManager.updateAppWidget(101, any()) }
        verify { appWidgetManager.updateAppWidget(102, any()) }
    }
}
