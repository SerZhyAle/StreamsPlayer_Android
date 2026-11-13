package com.sza.streamsplayer.core.util

import java.util.concurrent.CopyOnWriteArraySet

/**
 * The single process-wide answer to "may this animate".
 */
object AnimationPolicy {

    @Volatile
    private var currentDecision: PowerPolicyDecision = PowerPolicyDecision(
        PowerPolicyLevel.NORMAL,
        PowerPolicyReason.NONE
    )

    /** The level in force right now. */
    val level: PowerPolicyLevel
        get() = currentDecision.level

    /** The reason in force right now. */
    val reason: PowerPolicyReason
        get() = currentDecision.reason

    /**
     * True when transitions and decorative animators may run.
     */
    val isAnimationAllowed: Boolean
        get() = mayAnimate(AnimationIntent.DECORATIVE)

    /**
     * The policy matrix.
     */
    fun mayAnimate(intent: AnimationIntent): Boolean = when (currentDecision.level) {
        PowerPolicyLevel.NORMAL -> true
        PowerPolicyLevel.REDUCED -> intent != AnimationIntent.DECORATIVE
        PowerPolicyLevel.SAVING -> intent == AnimationIntent.FUNCTIONAL
    }

    private val listeners = CopyOnWriteArraySet<() -> Unit>()

    fun addLevelListener(listener: () -> Unit) {
        listeners.add(listener)
    }

    fun removeLevelListener(listener: () -> Unit) {
        listeners.remove(listener)
    }

    fun update(decision: PowerPolicyDecision) {
        if (currentDecision == decision) return
        currentDecision = decision
        listeners.forEach { it() }
    }
}
