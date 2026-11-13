package com.sza.streamsplayer.ui.common.widget

import android.content.Context
import android.util.AttributeSet
import com.google.android.material.checkbox.MaterialCheckBox

/**
 * Checkbox for boolean option rows (BOOLEAN-OPTION 2.0).
 *
 * Enforces contract section 3.5: "A disabled checkbox MUST NOT toggle from any activation path."
 * Native [android.widget.CompoundButton] does not check [isEnabled] prior to toggling.
 */
class SettingsCheckBox : MaterialCheckBox {

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    override fun toggle() {
        if (!isEnabled) return
        super.toggle()
    }

    override fun performClick(): Boolean {
        if (!isEnabled) return false
        return super.performClick()
    }
}
