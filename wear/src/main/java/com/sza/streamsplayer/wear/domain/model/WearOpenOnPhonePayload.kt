package com.sza.streamsplayer.wear.domain.model

import com.google.gson.annotations.SerializedName

/**
 * S2004: what the watch sends when it asks the phone to show a file, and what comes back.
 *
 * Mirrored verbatim in the phone module's copy of this file - the two are hand-kept in step, exactly as
 * the eleven paths before them.
 *
 * Field names are pinned rather than left to the module keep rule: the wire name is the whole contract
 * between two modules that share no code, and it must not depend on which module's shrinker config
 * happens to cover this package.
 */
data class WearOpenOnPhoneRequest(
    /**
     * The token the phone's own browse protocol issued for this item.
     *
     * The watch never invents an address: it echoes what the phone said, so the phone resolves it with
     * the same use case that listed it and no second addressing scheme exists to drift.
     */
    @SerializedName("token") val token: String,

    /** The name the notification is titled by, so the phone need not resolve the token to show it. */
    @SerializedName("displayName") val displayName: String,
    @SerializedName("mediaKind") val mediaKind: String? = null
)

/**
 * S0030: a request whose token starts with this prefix names a page of the shared media exchange portal, not a file.
 * The watch sends the page's name and never an address; the phone builds the address in its own language through its
 * portal resolver. Hand-kept in step with the phone module's `ExchangePortalLinks`.
 */
const val WEAR_PORTAL_TOKEN_PREFIX = "portal:"
const val WEAR_PORTAL_TOKEN_WATCH_EXCHANGE = "${WEAR_PORTAL_TOKEN_PREFIX}watch-exchange"
const val WEAR_PORTAL_MEDIA_KIND = "portal"

/**
 * S0035: a request whose token starts with this prefix names a surface of the help map, not a file. The watch sends
 * the surface's id and never an address; the phone opens the bundled help page of that surface in its own language
 * through its help resolver. Hand-kept in step with the phone module's `HelpSurfaceIds.WATCH_TOKEN_PREFIX`.
 */
const val WEAR_HELP_TOKEN_PREFIX = "help:"
const val WEAR_HELP_TOKEN_HOME = "${WEAR_HELP_TOKEN_PREFIX}wear:WearRoutes.HOME"
const val WEAR_HELP_MEDIA_KIND = "help"

/** S2004: what the phone did with the request. */
enum class WearOpenOnPhoneOutcome {

    /** The phone was in the foreground and opened the file there and then. */
    SHOWN,

    /** The phone was not in the foreground, so it posted a notification the user can tap. */
    NOTIFIED,

    /**
     * The phone could neither show nor notify - notifications are off for the app.
     *
     * Distinct from a lost link on purpose: the watch must be able to say why nothing happened, and
     * "the phone refused" and "the phone never answered" call for different words (strategic §11
     * criterion 9).
     */
    REFUSED_NO_NOTIFICATION,

    /** The token names nothing the phone can still open. */
    NOT_FOUND
}

/** S2004: the phone's answer to one [WearOpenOnPhoneRequest]. */
data class WearOpenOnPhoneAck(
    @SerializedName("token") val token: String,
    @SerializedName("outcome") val outcome: WearOpenOnPhoneOutcome
)
