package com.sza.streamsplayer.ui.common.widget

import android.content.Context
import android.content.res.TypedArray
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.content.res.use
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import com.google.android.material.checkbox.MaterialCheckBox
import com.sza.streamsplayer.R

/**
 * Canonical reusable boolean option row for settings fragments and forms (BOOLEAN-OPTION 2.0).
 *
 * Layout: leading square checkbox, text cell (title + inline help button, explanation below),
 * optional trailing action slot.
 *
 * Activation: intentional click/tap on checkbox, title caption or leading glyph toggles the boolean
 * exactly once (section 3 rule 1). Explanation, background and help do not toggle.
 *
 * Public XML attributes use the `str_` prefix (see `attrs.xml`).
 */
class SettingsToggleRow @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : LinearLayout(context, attrs, defStyleAttr), HelpableRow {

    private val switchView: MaterialCheckBox
    private val iconView: ImageView
    private val titleView: TextView
    private val subtitleView: TextView
    private val help: HelpRowDelegate
    private val trailingSlot: FrameLayout
    private val textGroup: LinearLayout
    private val titleLineSpacer: View

    private var checkedChangeListener: ((Boolean) -> Unit)? = null

    /**
     * Current checked state of the embedded checkbox.
     */
    var isChecked: Boolean
        get() = switchView.isChecked
        set(value) {
            switchView.isChecked = value
        }

    /**
     * `true` when the optional help icon is visible.
     */
    override val isHelpVisible: Boolean
        get() = help.isHelpVisible

    init {
        orientation = HORIZONTAL
        gravity = android.view.Gravity.TOP
        minimumHeight = resources.getDimensionPixelSize(R.dimen.ui_touch_target)
        isClickable = false
        isFocusable = false

        LayoutInflater.from(context).inflate(R.layout.view_settings_toggle_row, this, true)

        switchView = findViewById(R.id.str_switch)
        iconView = findViewById(R.id.str_icon)
        titleView = findViewById(R.id.str_title)
        subtitleView = findViewById(R.id.str_subtitle)
        help = HelpRowDelegate(findViewById(R.id.str_iconHelp), "SettingsToggleRow")
        trailingSlot = findViewById(R.id.str_trailingSlot)
        textGroup = findViewById(R.id.str_textGroup)
        titleLineSpacer = findViewById(R.id.str_titleLineSpacer)

        setupAccessibility()
        bindClicks()
        applyAttributes(attrs, defStyleAttr)
        help.syncVisibility()
    }

    /**
     * Sets the row title from a raw text value.
     */
    fun setTitle(text: CharSequence) {
        titleView.text = text
        updateAccessibilityDescriptions()
    }

    /**
     * Sets the row title from a string resource.
     */
    fun setTitle(@StringRes resId: Int) {
        setTitle(context.getText(resId))
    }

    /**
     * Sets the subtitle / explanation text. Empty/null hides the explanation.
     */
    fun setSubtitle(text: CharSequence?) {
        if (text.isNullOrEmpty()) {
            subtitleView.text = ""
            subtitleView.visibility = View.GONE
        } else {
            subtitleView.text = text
            subtitleView.visibility = View.VISIBLE
        }
        updateAccessibilityDescriptions()
    }

    /**
     * Sets the subtitle from a string resource.
     */
    fun setSubtitle(@StringRes resId: Int) {
        setSubtitle(context.getText(resId))
    }

    /**
     * Sets an optional leading feature icon. Passing `null` hides the icon slot.
     */
    fun setIcon(icon: Drawable?) {
        iconView.imageTintList = null
        iconView.clearColorFilter()
        iconView.setImageDrawable(icon)
        iconView.visibility = if (icon == null) View.GONE else View.VISIBLE
    }

    /**
     * Sets an optional leading feature icon from a drawable resource.
     */
    fun setIcon(@DrawableRes resId: Int) {
        val typedValue = TypedValue()
        context.theme.resolveAttribute(com.google.android.material.R.attr.colorOnSurfaceVariant, typedValue, true)
        iconView.imageTintList = android.content.res.ColorStateList.valueOf(typedValue.data)
        iconView.setImageResource(resId)
        iconView.visibility = View.VISIBLE
    }

    /**
     * Stores the help payload and makes the help icon available.
     */
    override fun setHelp(@StringRes titleRes: Int, @StringRes messageRes: Int) {
        help.setHelp(titleRes, messageRes)
    }

    /**
     * Shows or hides the help icon without dropping the stored help payload.
     */
    override fun setHelpVisible(visible: Boolean) {
        help.setHelpVisible(visible)
    }

    /**
     * Registers a listener invoked when the embedded checkbox changes state.
     */
    fun setOnCheckedChangeListener(listener: ((Boolean) -> Unit)?) {
        checkedChangeListener = listener
    }

    /**
     * Sets the checked state without invoking the change listener.
     */
    fun setCheckedSilently(value: Boolean) {
        val listener = checkedChangeListener
        checkedChangeListener = null
        switchView.isChecked = value
        checkedChangeListener = listener
    }

    /**
     * Forwards enabled state to all interactive children.
     */
    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
        switchView.isEnabled = enabled
        switchView.isClickable = enabled
        titleView.isEnabled = enabled
        titleView.isClickable = enabled
        iconView.isEnabled = enabled
        iconView.isClickable = enabled
        help.setEnabled(enabled)
        subtitleView.isEnabled = enabled
        alpha = if (enabled) 1f else 0.5f
    }

    fun setHugsTextContent(hug: Boolean) {
        val textGroupParams = textGroup.layoutParams as LayoutParams
        textGroupParams.width = if (hug) LayoutParams.WRAP_CONTENT else 0
        textGroupParams.weight = if (hug) 0f else 1f
        textGroup.layoutParams = textGroupParams

        val spacerParams = titleLineSpacer.layoutParams as LayoutParams
        spacerParams.weight = if (hug) 0f else 1f
        titleLineSpacer.layoutParams = spacerParams

        val subtitleParams = subtitleView.layoutParams as LayoutParams
        subtitleParams.width = if (hug) LayoutParams.WRAP_CONTENT else LayoutParams.MATCH_PARENT
        subtitleView.layoutParams = subtitleParams
    }

    fun setTrailingControl(view: View?) {
        trailingSlot.removeAllViews()
        if (view == null) {
            trailingSlot.visibility = View.GONE
            return
        }
        (view.parent as? ViewGroup)?.removeView(view)
        trailingSlot.addView(view)
        trailingSlot.visibility = View.VISIBLE
    }

    private fun bindClicks() {
        // BOOLEAN-OPTION 2.0 (section 3.1): intentional click/tap on checkbox, caption or glyph toggles once.
        // Explanation, background and help do NOT toggle.
        val toggleAction = OnClickListener { v ->
            if (!this@SettingsToggleRow.isEnabled || !v.isEnabled || !switchView.isEnabled) {
                return@OnClickListener
            }
            switchView.isChecked = !switchView.isChecked
        }
        titleView.setOnClickListener(toggleAction)
        iconView.setOnClickListener(toggleAction)

        switchView.setOnCheckedChangeListener { _, value ->
            checkedChangeListener?.invoke(value)
        }
    }

    private fun setupAccessibility() {
        // Single accessible control: switchView carries caption and description.
        titleView.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        subtitleView.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        iconView.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO

        ViewCompat.setAccessibilityDelegate(switchView, object : androidx.core.view.AccessibilityDelegateCompat() {
            override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfoCompat) {
                super.onInitializeAccessibilityNodeInfo(host, info)
                info.text = titleView.text
                val explanation = subtitleView.text
                if (!explanation.isNullOrBlank()) {
                    info.hintText = explanation
                }
            }
        })
    }

    private fun updateAccessibilityDescriptions() {
        val title = titleView.text
        switchView.contentDescription = title
    }

    private fun applyAttributes(attrs: AttributeSet?, defStyleAttr: Int) {
        if (attrs == null) return
        context.obtainStyledAttributes(attrs, R.styleable.SettingsToggleRow, defStyleAttr, 0).use { typedArray ->
            val title = typedArray.getText(R.styleable.SettingsToggleRow_str_title) ?: ""
            setTitle(title)
            setSubtitle(typedArray.getText(R.styleable.SettingsToggleRow_str_subtitle))
            help.setPayload(
                typedArray.getText(R.styleable.SettingsToggleRow_str_helpTitle),
                typedArray.getText(R.styleable.SettingsToggleRow_str_helpMessage)
            )
            help.applyInitialVisibility(typedArray.getBoolean(R.styleable.SettingsToggleRow_str_showHelp, false))
            val initiallyChecked = typedArray.getBoolean(R.styleable.SettingsToggleRow_str_checked, false)
            switchView.isChecked = initiallyChecked
            val iconRes = typedArray.getResourceId(R.styleable.SettingsToggleRow_str_icon, 0)
            if (iconRes != 0) setIcon(iconRes)
            applyTextSize(titleView, typedArray, R.styleable.SettingsToggleRow_str_titleTextSize)
            applyTextSize(subtitleView, typedArray, R.styleable.SettingsToggleRow_str_subtitleTextSize)
        }
    }

    private fun applyTextSize(view: TextView, typedArray: TypedArray, index: Int) {
        if (typedArray.hasValue(index)) {
            view.setTextSize(TypedValue.COMPLEX_UNIT_PX, typedArray.getDimension(index, view.textSize))
        }
    }
}
