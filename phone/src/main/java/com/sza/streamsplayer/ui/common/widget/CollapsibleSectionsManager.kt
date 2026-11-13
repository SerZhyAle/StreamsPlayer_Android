package com.sza.streamsplayer.ui.common.widget

import android.content.Context
import android.transition.AutoTransition
import android.transition.TransitionManager
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import com.sza.streamsplayer.core.util.AnimationPolicy
import com.sza.streamsplayer.data.local.preferences.CollapsibleSectionStore
import com.sza.streamsplayer.data.local.preferences.SharedPreferencesCollapsibleSectionStore

/**
 * Single orchestrator for collapsible groups across the app (COLLAPSIBLE-GROUP 1.1).
 *
 * Binds a [CollapsibleSectionHeader] to its content container: restores expanded state from the
 * [CollapsibleSectionStore] without animation, animates the body open/close on user toggle, and
 * persists every change.
 */
class CollapsibleSectionsManager(
    private val store: CollapsibleSectionStore,
) {

    private val headersByContainerId = mutableMapOf<Int, CollapsibleSectionHeader>()
    private val registeredHeaders = linkedSetOf<CollapsibleSectionHeader>()

    var onAnyExpandedChanged: ((Boolean) -> Unit)? = null

    constructor(context: Context) : this(SharedPreferencesCollapsibleSectionStore(context))

    /**
     * Registers one collapsible section.
     *
     * @param key caller-supplied `<screen>__<section>` identifier - the persistence key.
     * @param defaultExpanded state used when [key] has no saved value.
     * @param persistState `false` makes the section's state live only as long as the view.
     * @param onExpandedChanged optional hook invoked with the current expanded state on initial
     *   restore and on every user toggle.
     */
    fun register(
        header: CollapsibleSectionHeader,
        container: View,
        key: String,
        defaultExpanded: Boolean = false,
        persistState: Boolean = true,
        onExpandedChanged: ((Boolean) -> Unit)? = null,
    ) {
        if (container.id != View.NO_ID) {
            headersByContainerId[container.id] = header
        }
        registeredHeaders.add(header)
        val expanded = if (persistState) store.isExpanded(key, defaultExpanded) else defaultExpanded
        onExpandedChanged?.invoke(expanded)
        header.setExpanded(expanded, notify = false)
        container.isVisible = expanded
        onAnyExpandedChanged?.invoke(hasExpandedSection())
        header.setOnExpandedChangeListener { isExpanded ->
            onExpandedChanged?.invoke(isExpanded)
            (container.parent as? ViewGroup)?.let { parent ->
                if (AnimationPolicy.isAnimationAllowed) {
                    TransitionManager.beginDelayedTransition(parent, buildBodyTransition())
                }
            }
            // COLLAPSIBLE-GROUP 1.1 section 4.5: focus inside a body that hides goes to its header.
            if (!isExpanded && container.hasFocus()) header.requestFocus()
            container.isVisible = isExpanded
            if (persistState) {
                store.setExpanded(key, isExpanded)
            }
            onAnyExpandedChanged?.invoke(hasExpandedSection())
        }
    }

    /**
     * Collapses every registered section.
     */
    fun collapseAll() {
        for (header in registeredHeaders) {
            header.setExpanded(false)
        }
    }

    /** Whether at least one registered section is expanded right now. */
    fun hasExpandedSection(): Boolean = registeredHeaders.any { it.isExpanded() }

    /**
     * Expands whichever registered section encloses a row, named by that row's ancestor view ids.
     */
    fun expandSectionContaining(ancestorIds: List<Int>): Boolean {
        val header = ancestorIds.firstNotNullOfOrNull { headersByContainerId[it] } ?: return false
        header.setExpanded(true)
        return true
    }

    /**
     * Drops every registered header.
     */
    fun clear() {
        headersByContainerId.clear()
        registeredHeaders.clear()
    }

    private fun buildBodyTransition(): AutoTransition =
        AutoTransition().apply { duration = BODY_TRANSITION_DURATION_MS }

    companion object {
        private const val BODY_TRANSITION_DURATION_MS = 150L
    }
}
