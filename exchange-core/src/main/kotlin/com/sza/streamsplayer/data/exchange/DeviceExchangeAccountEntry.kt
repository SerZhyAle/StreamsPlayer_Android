package com.sza.streamsplayer.data.exchange

import java.util.Locale

/**
 * One account on one exchange server as this device holds it (contract DEVICE-EXCHANGE 5.2 and amendment D).
 * Every stream the client opens belongs to exactly one entry.
 */
data class DeviceExchangeAccountEntry(
    val host: String,
    val port: Int,
    val login: String,
    val deviceId: String,
    /** The leaf fingerprint the user accepted for a server the platform does not trust; null otherwise. */
    val pinnedCertificate: String? = null,
    val entryId: String = "",
    val pinnedPublicKey: String? = null,
) {

    /** Host lower-cased, port and login compared case-insensitively (5.1); the device token is kept under it. */
    val identity: String
        get() = host.lowercase(Locale.ROOT) + ":" + port + "/" + login.lowercase(Locale.ROOT)

    companion object {
        private val LOGIN = Regex("[A-Za-z0-9._-]{3,64}")

        /** The login form of 5.1: 3 to 64 characters of `[a-z0-9._-]`, compared case-insensitively. */
        fun isValidLogin(login: String?): Boolean = login != null && LOGIN.matches(login)
    }
}

/**
 * Where the device token of each account entry lives: Keystore-backed, never the password (5.2).
 * Seam defined in S0016, implemented with Room/Keystore in S0018.
 */
interface SecretStore {
    suspend fun token(entry: DeviceExchangeAccountEntry): String?
    suspend fun save(entry: DeviceExchangeAccountEntry, token: String): Boolean
    suspend fun forget(entry: DeviceExchangeAccountEntry)
}

typealias DeviceExchangeTokenStore = SecretStore

/**
 * Storage seam for account entries (S0016 seam, implemented with Room in S0018).
 * This product holds at most one account entry (Ask D, owner ruling 2026-10-10).
 */
interface EntryStore {
    suspend fun getEntry(): DeviceExchangeAccountEntry?
    suspend fun saveEntry(entry: DeviceExchangeAccountEntry): Boolean
    suspend fun removeEntry()
    suspend fun hasAccount(): Boolean

    suspend fun getAccountEntry(): ExchangeAccountEntry?
    suspend fun saveAccountEntry(entry: ExchangeAccountEntry): Boolean
    suspend fun updateState(entryId: String, state: ExchangeAccountState)
    suspend fun updateState(state: ExchangeAccountState)
    suspend fun getEntryById(entryId: String): ExchangeAccountEntry?
}
