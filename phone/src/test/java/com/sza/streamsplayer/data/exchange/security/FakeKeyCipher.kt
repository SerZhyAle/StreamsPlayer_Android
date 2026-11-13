package com.sza.streamsplayer.data.exchange.security

import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * In-JVM test implementation of [KeyCipher] using standard Java AES-GCM.
 * Supports simulating undecryptable tokens / corrupted keys.
 */
class FakeKeyCipher : KeyCipher {

    private val secretKey: SecretKey = KeyGenerator.getInstance("AES").apply {
        init(256, SecureRandom())
    }.generateKey()

    var simulateCorruptedKey: Boolean = false

    override fun encrypt(plaintext: ByteArray): KeyCipher.CipherResult {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(plaintext)
        return KeyCipher.CipherResult(ciphertext, iv)
    }

    override fun decrypt(ciphertext: ByteArray, iv: ByteArray): ByteArray {
        if (simulateCorruptedKey) {
            throw GeneralSecurityException("Simulated decryption failure (key invalidated or AEADBadTag)")
        }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(128, iv)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
        return cipher.doFinal(ciphertext)
    }
}
