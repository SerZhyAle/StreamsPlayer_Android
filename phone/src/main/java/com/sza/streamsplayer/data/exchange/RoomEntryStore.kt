package com.sza.streamsplayer.data.exchange

import com.sza.streamsplayer.data.local.db.ExchangeAccountEntryEntity
import com.sza.streamsplayer.data.local.db.ExchangeEntryDao
import timber.log.Timber
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Room-backed implementation of [EntryStore] (S0018 Scope 5).
 * Holds at most one account entry on this product (owner ruling 2026-10-10).
 */
@Singleton
class RoomEntryStore @Inject constructor(
    private val dao: ExchangeEntryDao,
) : EntryStore {

    override suspend fun getEntry(): DeviceExchangeAccountEntry? =
        dao.getEntry()?.toModel()?.toDeviceExchangeAccountEntry()

    override suspend fun getAccountEntry(): ExchangeAccountEntry? =
        dao.getEntry()?.toModel()

    override suspend fun hasAccount(): Boolean =
        dao.count() > 0

    override suspend fun getEntryById(entryId: String): ExchangeAccountEntry? =
        dao.getById(entryId)?.toModel()

    override suspend fun saveEntry(entry: DeviceExchangeAccountEntry): Boolean {
        val accountEntry = ExchangeAccountEntry(
            entryId = entry.entryId.ifEmpty { UUID.randomUUID().toString() },
            endpoint = "${entry.host.lowercase(Locale.ROOT)}:${entry.port}",
            login = entry.login.lowercase(Locale.ROOT),
            pin = entry.pinnedCertificate,
            pinnedPublicKey = entry.pinnedPublicKey,
            deviceId = entry.deviceId,
            deviceName = "Android",
        )
        return saveAccountEntry(accountEntry)
    }

    override suspend fun saveAccountEntry(entry: ExchangeAccountEntry): Boolean {
        val normalizedEndpoint = ExchangeAccountEntry.normalizeEndpoint(entry.endpoint)
        val normalizedLogin = ExchangeAccountEntry.normalizeLogin(entry.login)

        val existing = dao.getEntry()
        val entityToSave: ExchangeAccountEntryEntity

        if (existing != null) {
            val sameIdentity = existing.endpoint == normalizedEndpoint && existing.login == normalizedLogin
            if (sameIdentity) {
                // Re-enrolment of the same identity (Scope 2): reuse existing deviceId!
                Timber.d("S0018: re-enrolling existing entry id=${hashId(existing.entryId)}")
                entityToSave = existing.copy(
                    pin = entry.pin,
                    label = entry.label,
                    state = entry.state.name,
                    deviceName = entry.deviceName,
                    accountKind = entry.accountKind,
                    signInReason = entry.signInReason,
                    pinnedPublicKey = entry.pinnedPublicKey,
                )
                dao.updateMetadata(existing.entryId, entry.pin, entry.pinnedPublicKey, entry.label,
                    entry.state.name, entry.deviceName, entry.accountKind, entry.signInReason)
                return true
            } else {
                // A second sign-in while an entry exists is a replace (Scope 3):
                // The caller should have removed the old entry, but if not, replace here.
                Timber.d("S0018: replacing existing entry id=${hashId(existing.entryId)} with new entry")
                dao.deleteAll()
                entityToSave = ExchangeAccountEntryEntity.fromModel(
                    entry.copy(
                        endpoint = normalizedEndpoint,
                        login = normalizedLogin,
                    )
                )
            }
        } else {
            Timber.d("S0018: saving new entry id=${hashId(entry.entryId)}")
            entityToSave = ExchangeAccountEntryEntity.fromModel(
                entry.copy(
                    endpoint = normalizedEndpoint,
                    login = normalizedLogin,
                )
            )
        }

        Timber.d("S0018: saving entry deviceId=${hashId(entityToSave.deviceId)}")
        dao.insertOrReplace(entityToSave)
        return true
    }

    override suspend fun updateState(entryId: String, state: ExchangeAccountState) {
        Timber.d("S0018: updating state for entry id=${hashId(entryId)} to $state")
        dao.updateState(entryId, state.name)
    }

    override suspend fun updateState(state: ExchangeAccountState) {
        Timber.d("S0018: updating all entries state to $state")
        dao.updateAllState(state.name)
    }

    override suspend fun removeEntry() {
        val existing = dao.getEntry()
        if (existing != null) {
            Timber.d("S0018: removing entry id=${hashId(existing.entryId)}")
        }
        dao.deleteAll()
    }

    companion object {
        fun hashId(id: String): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(id.toByteArray(Charsets.UTF_8))
            return digest.take(4).joinToString("") { "%02x".format(it) }
        }
    }
}
