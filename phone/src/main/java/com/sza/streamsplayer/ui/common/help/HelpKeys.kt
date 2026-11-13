package com.sza.streamsplayer.ui.common.help

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.ContextWrapper
import android.view.KeyEvent
import android.view.Window
import androidx.appcompat.view.WindowCallbackWrapper

/**
 * F1 for the product's own surfaces (contract `CONTEXT-HELP` rule 1). Only a bare F1 pressed while one of
 * these surfaces has the focus is taken, and only when the help map gives the surface a page; it is never
 * registered system-wide, and no chord listed in S0035 section 2.2 is touched.
 */
object HelpKeys {

    /** A first, unmodified press of F1. */
    fun isHelpKey(event: KeyEvent): Boolean =
        event.keyCode == KeyEvent.KEYCODE_F1 &&
            event.action == KeyEvent.ACTION_DOWN &&
            event.repeatCount == 0 &&
            !event.isCtrlPressed && !event.isAltPressed && !event.isShiftPressed && !event.isMetaPressed

    /** Opens the help of [surfaceId] for an F1 [event] and returns true; returns false and does nothing otherwise. */
    fun handle(context: Context, surfaceId: String, event: KeyEvent): Boolean {
        if (!isHelpKey(event) || !HelpOpenManager.hasHelp(context, surfaceId)) return false
        HelpOpenManager.open(context, surfaceId)
        return true
    }

    /**
     * Makes F1 work inside a dialog or a sheet: a dialog window does not hand unused keys to its activity.
     * The key listener of the dialog is left alone; the window callback is wrapped.
     */
    fun install(dialog: Dialog?, surfaceId: String) {
        val window = dialog?.window ?: return
        val base = window.callback ?: return
        if (base is HelpWindowCallback) return
        window.callback = HelpWindowCallback(base, dialog, surfaceId)
    }

    private class HelpWindowCallback(
        base: Window.Callback,
        private val dialog: Dialog,
        private val surfaceId: String,
    ) : WindowCallbackWrapper(base) {
        override fun dispatchKeyEvent(event: KeyEvent): Boolean =
            handle(host(dialog.context), surfaceId, event) || super.dispatchKeyEvent(event)
    }

    private fun host(context: Context): Context {
        var current = context
        while (current is ContextWrapper) {
            if (current is Activity) return current
            current = current.baseContext
        }
        return context
    }
}
