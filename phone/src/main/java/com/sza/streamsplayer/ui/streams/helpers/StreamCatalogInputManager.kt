package com.sza.streamsplayer.ui.streams.helpers

import android.view.FocusFinder
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.ScrollView
import androidx.core.widget.NestedScrollView
import androidx.recyclerview.widget.RecyclerView
import com.sza.streamsplayer.core.input.GamepadNavIntent
import com.sza.streamsplayer.core.input.GamepadNavigationTranslator

/** Applies the donor's stick navigation and INPUT-PARITY context/search bindings to the catalog. */
class StreamCatalogInputManager(
    private val root: ViewGroup,
    private val search: View,
    private val onContext: (View?) -> Boolean,
) {
    private val translator = GamepadNavigationTranslator()

    fun handleKeyEvent(event: KeyEvent, focus: View?): Boolean {
        val context = event.keyCode == KeyEvent.KEYCODE_MENU ||
            event.keyCode == KeyEvent.KEYCODE_BUTTON_Y ||
            (event.keyCode == KeyEvent.KEYCODE_F10 && event.isShiftPressed)
        val find = event.keyCode == KeyEvent.KEYCODE_BUTTON_START ||
            (event.keyCode == KeyEvent.KEYCODE_F && event.isCtrlPressed)
        if (!context && !find) return false
        if (event.action == KeyEvent.ACTION_UP || event.repeatCount > 0) return true
        if (event.action != KeyEvent.ACTION_DOWN) return false
        return if (context) onContext(focus) else search.requestFocus()
    }

    fun handleMotionEvent(event: MotionEvent, focus: View?): Boolean =
        when (val intent = translator.translate(event)) {
            is GamepadNavIntent.FocusMove -> {
                val next = FocusFinder.getInstance().findNextFocus(root, focus, intent.direction)
                next != null && next !== focus && next.requestFocus()
            }
            is GamepadNavIntent.Scroll -> {
                val target = scrollContainer(focus)
                if (target == null) false else {
                    target.scrollBy(intent.dx, intent.dy)
                    true
                }
            }
            null -> false
        }

    private fun scrollContainer(focus: View?): View? {
        var view = focus
        while (view != null) {
            if (view is RecyclerView || view is ScrollView || view is NestedScrollView ||
                view is HorizontalScrollView) return view
            view = view.parent as? View
        }
        return null
    }
}
