package com.sza.streamsplayer.ui.dialog

import android.app.Dialog
import android.view.KeyEvent

object DialogKeyboardDelegate {
    fun applyTo(dialog: Dialog, onConfirm: () -> Unit) {
        dialog.setOnKeyListener { _, keyCode, event ->
            if (event.action != KeyEvent.ACTION_UP) return@setOnKeyListener false
            when (keyCode) {
                KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER, KeyEvent.KEYCODE_DPAD_CENTER -> {
                    onConfirm()
                    true
                }
                KeyEvent.KEYCODE_ESCAPE -> {
                    dialog.dismiss()
                    true
                }
                else -> false
            }
        }
    }
}
