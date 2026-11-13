package com.sza.streamsplayer.ui.common.support

import android.view.View
import androidx.core.view.AccessibilityDelegateCompat
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import com.sza.streamsplayer.R

/** BUTTONS 2.0 section 2.5: a control that opens a web address is announced as a link although it is drawn as a button. */
fun View.exposeAsLink() {
    ViewCompat.setAccessibilityDelegate(this, object : AccessibilityDelegateCompat() {
        override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfoCompat) {
            super.onInitializeAccessibilityNodeInfo(host, info)
            info.roleDescription = host.context.getString(R.string.a11y_role_link)
        }
    })
}
