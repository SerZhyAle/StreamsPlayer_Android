package com.sza.streamsplayer.data.exchange.drive

import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Google Play services' Authorization API for the one scope of the Drive channel ([DriveAppFolder.SCOPE],
 * non-sensitive). The Android OAuth client of `com.sza.streamsplayer` is found by Google from the package name
 * and the signing certificate - no client id, no secret and no key file is in this tree. The access token is
 * held for the duration of one call chain and never stored, logged or shown; the account's address is never read.
 */
@Singleton
class GoogleDriveAuthorizer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val channel: DriveChannelStore,
) : DriveAuthorizer {

    private val client by lazy { Identity.getAuthorizationClient(context) }

    private val request: AuthorizationRequest = AuthorizationRequest.builder()
        .setRequestedScopes(DriveAppFolder.REQUESTED_SCOPES.map(::Scope))
        .build()

    /** The scope URIs the authorization request names; a test pins them to the app folder alone. */
    internal val requestedScopeUris: List<String> get() = request.requestedScopes.map { it.scopeUri }

    private val _signInState = MutableStateFlow(stateOf(channel.signedIn))
    override val signInState: StateFlow<DriveSignInState> = _signInState.asStateFlow()

    override suspend fun token(): String {
        if (!channel.signedIn) throw DriveException.NotSignedIn()
        val result = try {
            client.authorize(request).await()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            Timber.d("S0029: authorization unavailable (${failure.javaClass.simpleName})")
            throw DriveException.Network(failure)
        }
        // A consent screen is wanted again: the grant was withdrawn in the Google account.
        if (result.hasResolution()) {
            markSignedOut()
            throw DriveException.Unauthorized()
        }
        return result.accessToken ?: throw DriveException.Unauthorized()
    }

    override suspend fun invalidate(token: String) {
        withContext(Dispatchers.IO) {
            try {
                GoogleAuthUtil.clearToken(context, token)
            } catch (failure: Exception) {
                Timber.d("S0029: token not cleared (${failure.javaClass.simpleName})")
            }
        }
    }

    override suspend fun authorize(): DriveAuthorization = try {
        outcomeOf(client.authorize(request).await())
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Exception) {
        Timber.d("S0029: authorization failed (${failure.javaClass.simpleName})")
        DriveAuthorization.Failed(failure.javaClass.simpleName)
    }

    override suspend fun complete(data: Intent?): DriveAuthorization = try {
        outcomeOf(client.getAuthorizationResultFromIntent(data))
    } catch (failure: Exception) {
        Timber.d("S0029: consent not completed (${failure.javaClass.simpleName})")
        DriveAuthorization.Failed(failure.javaClass.simpleName)
    }

    override suspend fun signOut() {
        // Our side of the sign-out is final at once; the consent itself stays in the person's Google account
        // settings, where they can withdraw it.
        markSignedOut()
        Timber.d("S0029: signed out of Drive")
    }

    private fun outcomeOf(result: AuthorizationResult): DriveAuthorization {
        val intent = result.pendingIntent
        if (result.hasResolution() && intent != null) return DriveAuthorization.NeedsConsent(intent)
        if (result.accessToken == null) return DriveAuthorization.Failed("no token")
        channel.signedIn = true
        _signInState.value = DriveSignInState.SIGNED_IN
        Timber.d("S0029: signed in to Drive")
        return DriveAuthorization.Granted
    }

    private fun markSignedOut() {
        channel.signedIn = false
        _signInState.value = DriveSignInState.SIGNED_OUT
    }

    private companion object {
        fun stateOf(signedIn: Boolean) = if (signedIn) DriveSignInState.SIGNED_IN else DriveSignInState.SIGNED_OUT
    }
}
