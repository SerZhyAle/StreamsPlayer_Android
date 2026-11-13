package com.sza.streamsplayer.ui.streams

import android.view.KeyEvent
import android.widget.EditText
import android.widget.LinearLayout
import androidx.test.core.app.ApplicationProvider
import com.sza.streamsplayer.ui.streams.helpers.StreamCatalogInputManager
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class StreamCatalogInputManagerTest {
    @Test
    fun contextButtonsOpenOnceAndTypingPassesThrough() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val root = LinearLayout(context)
        val search = EditText(context)
        root.addView(search)
        var opened = 0
        val input = StreamCatalogInputManager(root, search) { focus ->
            assertSame(search, focus)
            opened++
            true
        }
        for (code in listOf(KeyEvent.KEYCODE_BUTTON_Y, KeyEvent.KEYCODE_MENU)) {
            assertTrue(input.handleKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code), search))
            assertTrue(input.handleKeyEvent(KeyEvent(0, 0, KeyEvent.ACTION_DOWN, code, 1), search))
            assertTrue(input.handleKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code), search))
        }
        assertEquals(2, opened)
        assertFalse(input.handleKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_F), search))
        assertFalse(input.handleKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK), search))
        assertFalse(input.handleKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BUTTON_A), search))
    }
}
