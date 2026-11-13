package com.sza.streamsplayer.wear.data.exchange

import android.content.Context
import com.google.gson.Gson
import com.sza.streamsplayer.data.broadcast.BroadcastDescriptorDto
import com.sza.streamsplayer.data.exchange.security.AndroidKeyStoreCipher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.security.MessageDigest
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

/** The donor broadcast-pass store's encrypted descriptor path; account credentials never enter it. */
@Singleton
class WearBroadcastDescriptorStore @Inject constructor(
    @ApplicationContext context: Context,
    private val cipher: AndroidKeyStoreCipher,
) {
    private val prefs = context.getSharedPreferences("watch_broadcast_descriptors", Context.MODE_PRIVATE)
    private val gson = Gson()

    suspend fun save(url: String, descriptor: BroadcastDescriptorDto): Boolean = withContext(Dispatchers.IO) {
        val clear = gson.toJson(descriptor).toByteArray(Charsets.UTF_8)
        try {
            val encrypted = cipher.encrypt(clear)
            prefs.edit().putString(key(url), Base64.getEncoder().encodeToString(encrypted.ciphertext))
                .putString(key(url) + "/iv", Base64.getEncoder().encodeToString(encrypted.iv)).commit()
        } finally { clear.fill(0) }
    }

    suspend fun get(url: String): BroadcastDescriptorDto? = withContext(Dispatchers.IO) {
        val encrypted = prefs.getString(key(url), null) ?: return@withContext null
        try {
            val iv = prefs.getString(key(url) + "/iv", null) ?: throw IOException("Missing broadcast key data")
            val clear = cipher.decrypt(Base64.getDecoder().decode(encrypted), Base64.getDecoder().decode(iv))
            try { gson.fromJson(String(clear, Charsets.UTF_8), BroadcastDescriptorDto::class.java) }
            finally { clear.fill(0) }
        } catch (_: Exception) { throw IOException("Stored watch broadcast is unavailable") }
    }

    private fun key(url: String) = MessageDigest.getInstance("SHA-256").digest(url.trim().toByteArray())
        .joinToString("") { "%02x".format(it) }
}
