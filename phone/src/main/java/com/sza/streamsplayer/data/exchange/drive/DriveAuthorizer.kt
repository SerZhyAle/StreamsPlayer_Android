package com.sza.streamsplayer.data.exchange.drive

import android.app.PendingIntent
import android.content.Intent
import kotlinx.coroutines.flow.StateFlow

/** Where the access token for the Drive calls comes from. A token is never logged, stored or shown. */
interface DriveTokenSource {

    /** A current access token for [DriveAppFolder.SCOPE]; throws [DriveException.NotSignedIn] or [DriveException.Unauthorized]. */
    suspend fun token(): String

    /** The token was refused: forget it so the next [token] is a fresh one. */
    suspend fun invalidate(token: String)
}

/** Whether the person has signed this app in to Drive with their own Google account (S0029 scope 1). */
enum class DriveSignInState { SIGNED_OUT, SIGNED_IN }

/** What asking Google for the app-folder scope came to. */
sealed interface DriveAuthorization {

    /** The scope is granted; calls may go out. */
    data object Granted : DriveAuthorization

    /** Google needs the person to choose an account and consent: launch [pendingIntent], then pass its result to [DriveAuthorizer.complete]. */
    data class NeedsConsent(val pendingIntent: PendingIntent) : DriveAuthorization

    /** Google Play services could not be reached or refused. */
    data class Failed(val reason: String) : DriveAuthorization
}

/**
 * The Google side of the Drive channel. The interface lives in `src/main`; each variant's DI module binds the
 * implementation it ships (`CLAUDE.md` section 3). The scope asked for is always [DriveAppFolder.REQUESTED_SCOPES].
 */
interface DriveAuthorizer : DriveTokenSource {

    val signInState: StateFlow<DriveSignInState>

    /** Asks Google for the app-folder scope of the person's own account. */
    suspend fun authorize(): DriveAuthorization

    /** Reads the outcome of the consent screen [DriveAuthorization.NeedsConsent] launched. */
    suspend fun complete(data: Intent?): DriveAuthorization

    /** Sign out of Drive only; the exchange account is not touched (S0029 scope 1). */
    suspend fun signOut()
}

/** What the person has chosen to keep on this phone about the Drive channel: signed in or not, and this phone's id in its records. */
interface DriveChannelStore {
    var signedIn: Boolean

    /** Whether the first switch-on of the announcement has had its "what is written" notice. */
    var noticeShown: Boolean

    /** This phone's `deviceId` in the Drive records (22 characters, share id form), minted on first use. */
    val deviceId: String
}
