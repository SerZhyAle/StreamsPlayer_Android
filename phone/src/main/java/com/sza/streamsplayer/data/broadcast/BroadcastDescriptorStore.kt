package com.sza.streamsplayer.data.broadcast

import android.content.Context
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persists live broadcast descriptors for imported stream channels so that playback can
 * resolve their transport endpoints (LAN, RELAY, TUNNEL) and target latency.
 */
@Singleton
class BroadcastDescriptorStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

    // DEVICE-EXCHANGE item BF: an own broadcast's pair is held in this process's memory only, never in the preferences.
    private val ownPairs = ConcurrentHashMap<String, BroadcastCredentialsDto>()

    fun saveDescriptor(url: String, sourceId: String?, dto: BroadcastDescriptorDto) {
        val own = dto.credentials
        if (own == null) ownPairs.remove(url.trim()) else ownPairs[url.trim()] = own
        val json = gson.toJson(dto.copy(credentials = null))
        val editor = prefs.edit().putString(urlKey(url), json)
        sourceId?.takeIf { it.isNotBlank() }?.let {
            editor.putString(sourceIdKey(it), json)
        }
        editor.apply()
        Timber.d("S0020: saved broadcast descriptor for url=${BroadcastDescriptorParser.COMPRESSED_PREFIX}")
    }

    fun getByUrl(url: String): BroadcastDescriptorDto? {
        val json = prefs.getString(urlKey(url), null) ?: return null
        val stored = runCatching { gson.fromJson(json, BroadcastDescriptorDto::class.java) }.getOrNull()
        return ownPairs[url.trim()]?.let { stored?.copy(credentials = it) } ?: stored
    }

    fun getBySourceId(sourceId: String): BroadcastDescriptorDto? {
        val json = prefs.getString(sourceIdKey(sourceId), null) ?: return null
        return runCatching { gson.fromJson(json, BroadcastDescriptorDto::class.java) }.getOrNull()
    }

    fun remove(url: String, sourceId: String? = null) {
        ownPairs.remove(url.trim())
        val editor = prefs.edit().remove(urlKey(url))
        sourceId?.let { editor.remove(sourceIdKey(it)) }
        editor.apply()
    }

    private fun urlKey(url: String) = "url_${url.trim()}"
    private fun sourceIdKey(sourceId: String) = "sid_${sourceId.trim()}"

    private companion object {
        const val PREFS_NAME = "broadcast_descriptors"
    }
}
