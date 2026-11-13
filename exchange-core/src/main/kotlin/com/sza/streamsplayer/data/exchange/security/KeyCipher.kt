package com.sza.streamsplayer.data.exchange.security

/**
 * Seam for AES-256-GCM encryption of device tokens (S0018 Scope 4 and 3.1).
 * JVM unit tests use a fake cipher; Android emulator uses AndroidKeyStoreCipher.
 */
interface KeyCipher {
    fun encrypt(plaintext: ByteArray): CipherResult
    fun decrypt(ciphertext: ByteArray, iv: ByteArray): ByteArray

    data class CipherResult(
        val ciphertext: ByteArray,
        val iv: ByteArray,
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as CipherResult
            if (!ciphertext.contentEquals(other.ciphertext)) return false
            if (!iv.contentEquals(other.iv)) return false
            return true
        }

        override fun hashCode(): Int {
            var result = ciphertext.contentHashCode()
            result = 31 * result + iv.contentHashCode()
            return result
        }
    }
}
