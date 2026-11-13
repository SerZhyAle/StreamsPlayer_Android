package com.sza.streamsplayer.wear.data.exchange

import android.content.SharedPreferences
import com.sza.streamsplayer.data.exchange.security.KeyCipher
import io.mockk.every
import io.mockk.mockk
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

internal class WatchExchangeTestPreferences {
    val values = mutableMapOf<String, String>()
    var writable = true
    val prefs = mockk<SharedPreferences>()
    private val editor = mockk<SharedPreferences.Editor>()
    private val staged = mutableMapOf<String, String?>()
    init {
        every { prefs.getString(any(), any()) } answers { values[firstArg()] ?: secondArg() }
        every { prefs.edit() } answers { staged.clear(); editor }
        every { editor.putString(any(), any()) } answers { staged[firstArg()] = secondArg(); editor }
        every { editor.remove(any()) } answers { staged[firstArg()] = null; editor }
        every { editor.commit() } answers {
            if (writable) staged.forEach { (key, value) -> if (value == null) values.remove(key) else values[key] = value }
            writable
        }
    }
}

internal class WatchExchangeTestCipher : KeyCipher {
    var keyLost = false
    private val key = SecretKeySpec(ByteArray(32) { it.toByte() }, "AES")
    override fun encrypt(plaintext: ByteArray): KeyCipher.CipherResult {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        return KeyCipher.CipherResult(cipher.doFinal(plaintext), cipher.iv)
    }
    override fun decrypt(ciphertext: ByteArray, iv: ByteArray): ByteArray {
        check(!keyLost) { "Test key was lost" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
        return cipher.doFinal(ciphertext)
    }
}
