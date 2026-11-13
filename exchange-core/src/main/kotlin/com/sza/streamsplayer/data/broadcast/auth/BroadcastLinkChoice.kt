package com.sza.streamsplayer.data.broadcast.auth

import com.sza.streamsplayer.domain.model.BroadcastLinkPasswordMode

/**
 * What protects the outward link of an announced broadcast (contract LIVE-BROADCAST item R). [Open] has no link pair
 * at all, which the producer's servers and the relay read as "demand nothing" (DEVICE-EXCHANGE item DV).
 */
sealed interface BroadcastLinkChoice {

    /** The same choice in the vocabulary of the remembered setting, so a dialog can name what is live. */
    val mode: BroadcastLinkPasswordMode

    data object Generated : BroadcastLinkChoice {
        override val mode: BroadcastLinkPasswordMode = BroadcastLinkPasswordMode.AUTO
    }

    data class Custom(val password: String) : BroadcastLinkChoice {
        override val mode: BroadcastLinkPasswordMode get() = BroadcastLinkPasswordMode.CUSTOM

        // Never printed: the data-class default would put the password into any log line that formats it.
        override fun toString(): String = "Custom"
    }

    data object Open : BroadcastLinkChoice {
        override val mode: BroadcastLinkPasswordMode = BroadcastLinkPasswordMode.NONE
    }
}
