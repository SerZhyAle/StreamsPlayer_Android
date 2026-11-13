package com.sza.streamsplayer.wear.data.exchange

import android.content.SharedPreferences
import com.google.gson.Gson
import com.sza.streamsplayer.data.exchange.DeviceExchangeAccountEntry
import com.sza.streamsplayer.data.exchange.EntryStore
import com.sza.streamsplayer.data.exchange.ExchangeAccountEntry
import com.sza.streamsplayer.data.exchange.ExchangeAccountState
import com.sza.streamsplayer.data.exchange.SecretStore
import com.sza.streamsplayer.data.exchange.security.KeyCipher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.Base64

/** Donor encrypted-entry store adapted to this product's one account and the shared storage seams. */
class WearExchangeEntryStore(
    private val prefs: SharedPreferences,
    private val cipher: KeyCipher,
    private val gson: Gson = Gson(),
) : EntryStore, SecretStore {
    private class Snapshot(@com.google.gson.annotations.SerializedName("entry") val entry: ExchangeAccountEntry,
        @com.google.gson.annotations.SerializedName("token") val token: String?) {
        override fun toString() = "Watch exchange credentials (***)"
    }

    private val writes = Mutex()
    private var loaded = false
    private var held: Snapshot? = null
    private val state = MutableStateFlow<ExchangeAccountEntry?>(null)
    val entries = state.onStart { getAccountEntry() }

    override suspend fun getAccountEntry(): ExchangeAccountEntry? = read { it?.entry }
    override suspend fun getEntry(): DeviceExchangeAccountEntry? = getAccountEntry()?.toDeviceExchangeAccountEntry()
    override suspend fun hasAccount(): Boolean = getAccountEntry() != null
    override suspend fun getEntryById(entryId: String): ExchangeAccountEntry? =
        getAccountEntry()?.takeIf { it.entryId == entryId }

    override suspend fun saveAccountEntry(entry: ExchangeAccountEntry): Boolean = mutate { previous ->
        Snapshot(entry, previous?.token?.takeIf { previous.entry.deviceId == entry.deviceId })
    }

    override suspend fun saveEntry(entry: DeviceExchangeAccountEntry): Boolean = saveAccountEntry(account(entry))

    override suspend fun removeEntry() {
        check(mutate { null }) { "Watch account removal could not be stored" }
    }

    override suspend fun updateState(entryId: String, state: ExchangeAccountState) {
        check(mutate { it?.let { old ->
            if (old.entry.entryId == entryId) Snapshot(old.entry.copy(state = state), old.token) else old
        } })
    }

    override suspend fun updateState(state: ExchangeAccountState) {
        check(mutate { it?.let { old -> Snapshot(old.entry.copy(state = state), old.token) } })
    }

    override suspend fun token(entry: DeviceExchangeAccountEntry): String? =
        read { it?.takeIf { snapshot -> snapshot.entry.deviceId == entry.deviceId }?.token }

    override suspend fun save(entry: DeviceExchangeAccountEntry, token: String): Boolean = mutate { previous ->
        Snapshot(previous?.entry?.takeIf { it.deviceId == entry.deviceId } ?: account(entry), token)
    }

    override suspend fun forget(entry: DeviceExchangeAccountEntry) {
        check(mutate { old -> old?.let {
            if (it.entry.deviceId == entry.deviceId) Snapshot(
                it.entry.copy(state = ExchangeAccountState.NeedsSignIn), null,
            ) else it
        } })
    }

    private suspend fun <T> read(block: (Snapshot?) -> T): T = withContext(Dispatchers.IO) {
        writes.withLock { block(load()) }
    }

    private suspend fun mutate(change: (Snapshot?) -> Snapshot?): Boolean = withContext(Dispatchers.IO) {
        writes.withLock {
            val next = change(load())
            val saved = try {
                val edit = prefs.edit()
                if (next == null) {
                    edit.remove(KEY_CIPHERTEXT).remove(KEY_IV)
                } else {
                    val clear = gson.toJson(next).toByteArray(Charsets.UTF_8)
                    val encrypted = try { cipher.encrypt(clear) } finally { clear.fill(0) }
                    edit.putString(KEY_CIPHERTEXT, Base64.getEncoder().encodeToString(encrypted.ciphertext))
                        .putString(KEY_IV, Base64.getEncoder().encodeToString(encrypted.iv))
                }
                edit.commit()
            } catch (failure: Exception) {
                Timber.w("Watch exchange store refused a write (%s)", failure.javaClass.simpleName)
                false
            }
            if (saved) {
                held = next
                state.value = next?.entry
            }
            saved
        }
    }

    private fun load(): Snapshot? {
        if (loaded) return held
        held = try {
            val encrypted = prefs.getString(KEY_CIPHERTEXT, null)
            val iv = prefs.getString(KEY_IV, null)
            if (encrypted == null || iv == null) null else {
                val clear = cipher.decrypt(Base64.getDecoder().decode(encrypted), Base64.getDecoder().decode(iv))
                try {
                    gson.fromJson(String(clear, Charsets.UTF_8), Snapshot::class.java)?.takeIf {
                        val required: List<String?> = listOf(it.entry?.endpoint, it.entry?.login, it.entry?.deviceId)
                        required.none { value -> value.isNullOrBlank() }
                    }
                } finally { clear.fill(0) }
            }
        } catch (failure: Exception) {
            // No account password can repair a lost platform key; a new phone hand-over can.
            Timber.w("Watch exchange credentials could not be opened (%s)", failure.javaClass.simpleName)
            null
        }
        loaded = true
        state.value = held?.entry
        return held
    }

    private fun account(entry: DeviceExchangeAccountEntry) = ExchangeAccountEntry(
        entryId = entry.entryId.ifBlank { entry.deviceId },
        endpoint = if (entry.host.contains(':')) "[${entry.host}]:${entry.port}" else "${entry.host}:${entry.port}",
        login = entry.login, pin = entry.pinnedCertificate, pinnedPublicKey = entry.pinnedPublicKey,
        deviceId = entry.deviceId, deviceName = "Wear OS",
    )

    private companion object {
        const val KEY_CIPHERTEXT = "exchange_account_v1"
        const val KEY_IV = "exchange_account_iv_v1"
    }
}
