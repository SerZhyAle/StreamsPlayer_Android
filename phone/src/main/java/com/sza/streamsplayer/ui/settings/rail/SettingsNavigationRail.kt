package com.sza.streamsplayer.ui.settings.rail

import android.content.Context
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import com.sza.streamsplayer.R

/**
 * Vertical settings navigation rail component (SETTINGS-NAVIGATION 2.0).
 *
 * Requirements:
 * - Vertical menu at reading start.
 * - Ordinary touch target floor: 48 dp (dimens_ui.xml: [R.dimen.ui_touch_target]).
 * - Non-color selection cue: visible indicator bar and bold font weight (section 2 rule 7).
 * - Single child level with 12 dp indentation (section 2 rule 4).
 * - Coherent glyph presentation across all destinations.
 */
class SettingsNavigationRail @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : LinearLayout(context, attrs, defStyleAttr) {

    private val destinations = mutableListOf<SettingsDestination>()
    private val itemViews = mutableMapOf<String, View>()
    private var selectedDestinationId: String? = null
    private var listener: ((SettingsDestination) -> Unit)? = null

    private val childIndentPx = resources.getDimensionPixelSize(R.dimen.ui_rail_child_indent)

    init {
        orientation = VERTICAL
    }

    /**
     * Sets the destination list and builds the navigation rows.
     */
    fun setDestinations(items: List<SettingsDestination>) {
        destinations.clear()
        destinations.addAll(items)
        itemViews.clear()
        removeAllViews()

        val inflater = LayoutInflater.from(context)
        for (dest in items) {
            val itemView = inflater.inflate(R.layout.item_settings_navigation_rail, this, false)
            val iconView = itemView.findViewById<ImageView>(R.id.rail_item_icon)
            val titleView = itemView.findViewById<TextView>(R.id.rail_item_title)

            iconView.setImageResource(dest.iconRes)
            titleView.setText(dest.titleRes)

            if (dest.isChild) {
                // SETTINGS-NAVIGATION 2.0 section 2 rule 4: single child level indentation
                val startPad = itemView.paddingStart + childIndentPx
                itemView.setPaddingRelative(startPad, itemView.paddingTop, itemView.paddingEnd, itemView.paddingBottom)
            }

            itemView.setOnClickListener {
                selectDestination(dest.id, notifyListener = true)
            }

            // Accessible navigation row semantics
            ViewCompat.setAccessibilityDelegate(itemView, object : androidx.core.view.AccessibilityDelegateCompat() {
                override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfoCompat) {
                    super.onInitializeAccessibilityNodeInfo(host, info)
                    info.roleDescription = host.context.getString(R.string.a11y_role_tab)
                    info.isSelected = (dest.id == selectedDestinationId)
                }
            })

            itemViews[dest.id] = itemView
            addView(itemView)
        }

        if (selectedDestinationId != null) {
            updateVisualSelection(selectedDestinationId)
        } else if (items.isNotEmpty()) {
            selectDestination(items.first().id, notifyListener = false)
        }
    }

    /**
     * Programmatically selects the destination with [id].
     */
    fun selectDestination(id: String, notifyListener: Boolean = false) {
        if (selectedDestinationId == id && !notifyListener) return
        selectedDestinationId = id
        updateVisualSelection(id)

        if (notifyListener) {
            val dest = destinations.find { it.id == id }
            if (dest != null) {
                listener?.invoke(dest)
            }
        }
    }

    /** Gives the keyboard focus to the selected row, as when the page list comes back below the breakpoint (section 2.6). */
    fun focusSelected() {
        itemViews[selectedDestinationId]?.requestFocus()
    }

    /**
     * Current selected destination id, or null if empty.
     */
    fun getSelectedDestinationId(): String? = selectedDestinationId

    /**
     * Registers a listener invoked when the user selects a destination.
     */
    fun setOnDestinationSelectedListener(listener: ((SettingsDestination) -> Unit)?) {
        this.listener = listener
    }

    private fun updateVisualSelection(activeId: String?) {
        for ((id, view) in itemViews) {
            val isSelected = (id == activeId)
            view.isSelected = isSelected

            val indicator = view.findViewById<View>(R.id.rail_item_indicator)
            val titleView = view.findViewById<TextView>(R.id.rail_item_title)
            val iconView = view.findViewById<ImageView>(R.id.rail_item_icon)

            // Section 2 rule 7: explicit non-color selection cue (indicator bar + bold text)
            indicator.visibility = if (isSelected) View.VISIBLE else View.INVISIBLE
            titleView.setTypeface(null, if (isSelected) Typeface.BOLD else Typeface.NORMAL)

            // Dynamic color tint for selected vs normal
            val tintColor = if (isSelected) {
                com.google.android.material.color.MaterialColors.getColor(
                    view,
                    androidx.appcompat.R.attr.colorPrimary,
                    android.graphics.Color.WHITE
                )
            } else {
                com.google.android.material.color.MaterialColors.getColor(
                    view,
                    com.google.android.material.R.attr.colorOnSurfaceVariant,
                    android.graphics.Color.GRAY
                )
            }
            iconView.setColorFilter(tintColor)
        }
    }
}
