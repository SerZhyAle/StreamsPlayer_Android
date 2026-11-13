package com.sza.streamsplayer.data.exchange.drive

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import com.sza.streamsplayer.data.repository.settings.BroadcastSettingsStore
import com.sza.streamsplayer.domain.model.AppSettings
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S0029 scope 1 and 4: the authorization request names the app folder and nothing wider, and the announcement is
 * off until the person turns it on, separately for audio and for the camera.
 */
class DriveChannelScopeAndDefaultsTest {

    @Test
    fun `the authorization request asks for the app folder scope alone`() {
        val authorizer = GoogleDriveAuthorizer(mockk<Context>(relaxed = true), FakeDriveChannelStore(signedIn = false))

        assertEquals(listOf("https://www.googleapis.com/auth/drive.appdata"), authorizer.requestedScopeUris)
        assertEquals(DriveAppFolder.REQUESTED_SCOPES, authorizer.requestedScopeUris)
    }

    @Test
    fun `the sign-in state follows the stored choice and signing out of Drive is its own action`() {
        val signedIn = GoogleDriveAuthorizer(mockk<Context>(relaxed = true), FakeDriveChannelStore(signedIn = true))
        val signedOut = GoogleDriveAuthorizer(mockk<Context>(relaxed = true), FakeDriveChannelStore(signedIn = false))

        assertEquals(DriveSignInState.SIGNED_IN, signedIn.signInState.value)
        assertEquals(DriveSignInState.SIGNED_OUT, signedOut.signInState.value)
    }

    @Test
    fun `a call while signed out is refused before any request leaves`() = kotlinx.coroutines.runBlocking {
        val authorizer = GoogleDriveAuthorizer(mockk<Context>(relaxed = true), FakeDriveChannelStore(signedIn = false))
        try {
            authorizer.token()
            org.junit.Assert.fail("expected NotSignedIn")
        } catch (failure: DriveException.NotSignedIn) {
            assertTrue(failure.final)
        }
    }

    @Test
    fun `signing out clears the stored choice and leaves the rest alone`() = kotlinx.coroutines.runBlocking {
        val channel = FakeDriveChannelStore(signedIn = true, noticeShown = true)
        val authorizer = GoogleDriveAuthorizer(mockk<Context>(relaxed = true), channel)

        authorizer.signOut()

        assertFalse(channel.signedIn)
        assertEquals(DriveSignInState.SIGNED_OUT, authorizer.signInState.value)
        assertEquals(FakeDriveChannelStore.OWN_DEVICE_ID, channel.deviceId)
    }

    @Test
    fun `the announcement is off by default for audio and for the camera`() {
        val read = BroadcastSettingsStore.read(emptyPreferences())

        assertFalse(read.broadcast.publishAudioToDrive)
        assertFalse(read.broadcast.publishVideoToDrive)
        assertFalse(AppSettings().broadcast.publishAudioToDrive)
        assertFalse(AppSettings().broadcast.publishVideoToDrive)
    }

    @Test
    fun `the choice is remembered per mode and the retired exchange server switch is dropped`() {
        val retired = booleanPreferencesKey("broadcast_publish_audio_to_exchange")
        val preferences = mutablePreferencesOf(retired to true)
        val settings = AppSettings().let {
            it.copy(broadcast = it.broadcast.copy(publishAudioToDrive = true))
        }

        BroadcastSettingsStore.write(preferences, settings)
        val read = BroadcastSettingsStore.read(preferences).broadcast

        assertTrue(read.publishAudioToDrive)
        assertFalse(read.publishVideoToDrive)
        assertFalse(preferences.contains(retired))
    }
}
