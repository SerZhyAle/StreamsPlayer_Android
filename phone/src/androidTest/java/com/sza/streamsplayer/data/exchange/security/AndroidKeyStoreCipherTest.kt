package com.sza.streamsplayer.data.exchange.security

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.security.KeyStore
import javax.crypto.AEADBadTagException

/**
 * Instrumented test for [AndroidKeyStoreCipher] verifying real AndroidKeyStore AES-256-GCM behavior on device/emulator.
 * Satisfies S0018 DoD item:
 * "The instrumented AndroidKeyStore test on the emulator exits 0, command and output cited."
 */
@RunWith(AndroidJUnit4::class)
class AndroidKeyStoreCipherTest {

    private lateinit var cipher: AndroidKeyStoreCipher

    @Before
    fun setUp() {
        // Clear any leftover key alias from previous test runs
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (ks.containsAlias(AndroidKeyStoreCipher.KEY_ALIAS)) {
            ks.deleteEntry(AndroidKeyStoreCipher.KEY_ALIAS)
        }
        cipher = AndroidKeyStoreCipher()
    }

    @Test
    fun encryptAndDecrypt_roundTripSucceeds() {
        val originalToken = "device-exchange-bearer-token-secret-123456789".toByteArray(Charsets.UTF_8)
        val result = cipher.encrypt(originalToken)

        assertNotNull(result.ciphertext)
        assertNotNull(result.iv)
        assertEquals(12, result.iv.size) // Standard 96-bit GCM IV
        assertFalse(result.ciphertext.contentEquals(originalToken))

        val decrypted = cipher.decrypt(result.ciphertext, result.iv)
        assertArrayEquals(originalToken, decrypted)
        assertEquals("device-exchange-bearer-token-secret-123456789", String(decrypted, Charsets.UTF_8))
    }

    @Test
    fun decryptWithTamperedCiphertext_throwsAEADBadTagException() {
        val originalToken = "device-exchange-bearer-token-secret-123456789".toByteArray(Charsets.UTF_8)
        val result = cipher.encrypt(originalToken)

        // Tamper with ciphertext
        val tamperedCiphertext = result.ciphertext.copyOf()
        tamperedCiphertext[0] = (tamperedCiphertext[0].toInt() xor 0xFF).toByte()

        try {
            cipher.decrypt(tamperedCiphertext, result.iv)
            fail("Expected AEADBadTagException or GeneralSecurityException on tampered ciphertext")
        } catch (e: Exception) {
            assertTrue(
                "Expected authentication tag failure, got ${e::class.java.name}: ${e.message}",
                e is AEADBadTagException || e.cause is AEADBadTagException || e is javax.crypto.BadPaddingException
            )
        }
    }

    @Test
    fun keyPersistsAcrossCipherInstances() {
        val originalToken = "persistent-token".toByteArray(Charsets.UTF_8)
        val result = cipher.encrypt(originalToken)

        // Create new cipher instance accessing existing KeyStore alias
        val secondCipherInstance = AndroidKeyStoreCipher()
        val decrypted = secondCipherInstance.decrypt(result.ciphertext, result.iv)
        assertArrayEquals(originalToken, decrypted)
    }
}
