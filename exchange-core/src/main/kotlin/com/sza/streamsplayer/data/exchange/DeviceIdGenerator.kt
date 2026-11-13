package com.sza.streamsplayer.data.exchange

import java.security.SecureRandom
import java.util.Base64

/**
 * Generates and validates deviceId according to DEVICE-EXCHANGE section 2 and ANYWHERE-ACCESS 6.2:
 * 16 random bytes in the share id form (base64url without padding, 22 characters).
 */
object DeviceIdGenerator {
    fun generate(random: SecureRandom = SecureRandom()): String {
        val bytes = ByteArray(16)
        random.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    fun isValid(deviceId: String?): Boolean {
        if (deviceId == null || deviceId.length != 22) return false
        return deviceId.all { it in 'A'..'Z' || it in 'a'..'z' || it in '0'..'9' || it == '-' || it == '_' }
    }
}
