package com.sza.streamsplayer.core.util

/**
 * What a call site's animation is FOR, declared by the caller.
 */
enum class AnimationIntent {
    /** Pure ornament - screen transitions, crossfades. */
    DECORATIVE,

    /** Meaningful, but a continuous full-screen redraw. */
    AMBIENT,

    /** Bounded state feedback - focus ring, control toggles. */
    FUNCTIONAL
}
