package com.sza.streamsplayer.core.util

/**
 * The reason calculated by the power policy resolver.
 */
enum class PowerPolicyReason {
    NONE,
    USER_ALWAYS,
    SYSTEM_SAVER,
    LOW_BATTERY,
    ANIMATION_SWITCH
}

/**
 * Holds the active [PowerPolicyLevel] and the [PowerPolicyReason] that decided it.
 */
data class PowerPolicyDecision(
    val level: PowerPolicyLevel,
    val reason: PowerPolicyReason
)
