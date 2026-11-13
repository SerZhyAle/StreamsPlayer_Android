package com.sza.streamsplayer.data.exchange

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** Only the acknowledged device and node ids are retained, so an absent watch can still be revoked. */
@Singleton
class WatchExchangeDeviceStore @Inject constructor(@ApplicationContext context: Context) {
    private val preferences = context.getSharedPreferences("exchange_watch_devices", Context.MODE_PRIVATE)

    suspend fun remember(entry: DeviceExchangeAccountEntry, nodeId: String, deviceId: String) =
        withContext(Dispatchers.IO) {
            check(preferences.edit().putString(key(entry, nodeId), deviceId).commit())
        }

    suspend fun devices(entry: DeviceExchangeAccountEntry): Map<String, String> = withContext(Dispatchers.IO) {
        val prefix = "${entry.entryId}/"
        preferences.all.entries.filter { it.key.startsWith(prefix) && it.value is String }
            .associate { it.key.removePrefix(prefix) to it.value as String }
    }

    suspend fun forget(entry: DeviceExchangeAccountEntry) = withContext(Dispatchers.IO) {
        val edit = preferences.edit()
        preferences.all.keys.filter { it.startsWith("${entry.entryId}/") }.forEach(edit::remove)
        check(edit.commit())
    }

    private fun key(entry: DeviceExchangeAccountEntry, nodeId: String) = "${entry.entryId}/$nodeId"
}
