package com.sza.streamsplayer.data.exchange

import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-memory fallback / testing implementation of [SecretStore] for S0016.
 * Replaced by Room/Keystore persistence in S0018.
 */
@Singleton
class InMemorySecretStore @Inject constructor() : SecretStore {
    private val tokens = ConcurrentHashMap<String, String>()

    override suspend fun token(entry: DeviceExchangeAccountEntry): String? =
        tokens[entry.identity]

    override suspend fun save(entry: DeviceExchangeAccountEntry, token: String): Boolean {
        tokens[entry.identity] = token
        return true
    }

    override suspend fun forget(entry: DeviceExchangeAccountEntry) {
        tokens.remove(entry.identity)
    }
}

/**
 * In-memory fallback / testing implementation of [EntryStore] for S0016.
 * Replaced by Room persistence in S0018.
 */
@Singleton
class InMemoryEntryStore @Inject constructor() : EntryStore {
    @Volatile
    private var currentEntry: ExchangeAccountEntry? = null

    override suspend fun getEntry(): DeviceExchangeAccountEntry? =
        currentEntry?.toDeviceExchangeAccountEntry()

    override suspend fun saveEntry(entry: DeviceExchangeAccountEntry): Boolean {
        currentEntry = ExchangeAccountEntry(
            entryId = entry.entryId.ifEmpty { UUID.randomUUID().toString() },
            endpoint = "${entry.host.lowercase(Locale.ROOT)}:${entry.port}",
            login = entry.login.lowercase(Locale.ROOT),
            pin = entry.pinnedCertificate,
            deviceId = entry.deviceId,
            deviceName = "Android",
        )
        return true
    }

    override suspend fun removeEntry() {
        currentEntry = null
    }

    override suspend fun hasAccount(): Boolean = currentEntry != null

    override suspend fun getAccountEntry(): ExchangeAccountEntry? = currentEntry

    override suspend fun saveAccountEntry(entry: ExchangeAccountEntry): Boolean {
        currentEntry = entry
        return true
    }

    override suspend fun updateState(entryId: String, state: ExchangeAccountState) {
        if (currentEntry?.entryId == entryId) {
            currentEntry = currentEntry?.copy(state = state)
        }
    }

    override suspend fun updateState(state: ExchangeAccountState) {
        currentEntry = currentEntry?.copy(state = state)
    }

    override suspend fun getEntryById(entryId: String): ExchangeAccountEntry? =
        if (currentEntry?.entryId == entryId) currentEntry else null
}

/**
 * In-memory implementation of [com.sza.streamsplayer.data.exchange.session.DeviceExchangeE2eKeyStore] for unit tests.
 */
@Singleton
class InMemoryDeviceExchangeE2eKeyStore @Inject constructor() : com.sza.streamsplayer.data.exchange.session.DeviceExchangeE2eKeyStore {
    private val keys = ConcurrentHashMap<String, com.sza.streamsplayer.data.security.e2e.E2eDeviceKey>()

    override suspend fun keyFor(deviceId: String): com.sza.streamsplayer.data.security.e2e.E2eDeviceKey =
        keys.getOrPut(deviceId) { com.sza.streamsplayer.data.security.e2e.E2eDeviceKey.generate() }

    override suspend fun existing(deviceId: String): com.sza.streamsplayer.data.security.e2e.E2eDeviceKey? =
        keys[deviceId]

    override suspend fun forget(deviceId: String) {
        keys.remove(deviceId)
    }
}
