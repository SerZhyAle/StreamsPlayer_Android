package com.sza.streamsplayer.data.exchange

import com.sza.streamsplayer.data.exchange.security.KeyCipher
import com.sza.streamsplayer.data.local.db.ExchangeEntryDao
import timber.log.Timber
import java.security.MessageDigest
import java.util.Base64
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Secret store keeping device tokens encrypted via [KeyCipher] in Room beside the entry id (S0018 Scope 4).
 * If a token cannot be decrypted (key invalidated, restored from backup), moves the entry to NeedsSignIn
 * and preserves login, pin and deviceId. Never deletes silently and never retries in a loop.
 */
@Singleton
class RoomSecretStore @Inject constructor(
    private val dao: ExchangeEntryDao,
    private val keyCipher: KeyCipher,
    private val entryStore: EntryStore,
) : SecretStore {

    override suspend fun token(entry: DeviceExchangeAccountEntry): String? {
        val entity = (if (entry.entryId.isNotEmpty()) dao.getById(entry.entryId) else null)
            ?: dao.getByIdentity(
                endpoint = if (entry.host.contains(':')) "[${entry.host.lowercase(Locale.ROOT)}]:${entry.port}" else "${entry.host.lowercase(Locale.ROOT)}:${entry.port}",
                login = entry.login.lowercase(Locale.ROOT),
            )
            ?: return null

        val ciphertextB64 = entity.tokenCiphertext ?: return null
        val ivB64 = entity.tokenIv ?: return null

        val ciphertext = try {
            Base64.getDecoder().decode(ciphertextB64)
        } catch (e: Exception) {
            Timber.d(e, "S0018: invalid base64 token ciphertext for entryId=${hashId(entity.entryId)}; moving to NeedsSignIn")
            entryStore.updateState(entity.entryId, ExchangeAccountState.NeedsSignIn)
            return null
        }

        val iv = try {
            Base64.getDecoder().decode(ivB64)
        } catch (e: Exception) {
            Timber.d(e, "S0018: invalid base64 token iv for entryId=${hashId(entity.entryId)}; moving to NeedsSignIn")
            entryStore.updateState(entity.entryId, ExchangeAccountState.NeedsSignIn)
            return null
        }

        return try {
            val decrypted = keyCipher.decrypt(ciphertext, iv)
            String(decrypted, Charsets.UTF_8)
        } catch (e: Exception) {
            Timber.d(e, "S0018: token decryption failed for entryId=${hashId(entity.entryId)}; moving to NeedsSignIn")
            entryStore.updateState(entity.entryId, ExchangeAccountState.NeedsSignIn)
            null
        }
    }

    override suspend fun save(entry: DeviceExchangeAccountEntry, token: String): Boolean {
        val entity = (if (entry.entryId.isNotEmpty()) dao.getById(entry.entryId) else null)
            ?: dao.getByIdentity(
                endpoint = if (entry.host.contains(':')) "[${entry.host.lowercase(Locale.ROOT)}]:${entry.port}" else "${entry.host.lowercase(Locale.ROOT)}:${entry.port}",
                login = entry.login.lowercase(Locale.ROOT),
            )
            ?: return false

        return try {
            val result = keyCipher.encrypt(token.toByteArray(Charsets.UTF_8))
            val ciphertextB64 = Base64.getEncoder().encodeToString(result.ciphertext)
            val ivB64 = Base64.getEncoder().encodeToString(result.iv)
            dao.updateToken(entity.entryId, ciphertextB64, ivB64)
            Timber.d("S0018: saved token ciphertext for entryId=${hashId(entity.entryId)}")
            true
        } catch (e: Exception) {
            Timber.d(e, "S0018: token encryption failed for entryId=${hashId(entity.entryId)}")
            false
        }
    }

    override suspend fun forget(entry: DeviceExchangeAccountEntry) {
        val entity = (if (entry.entryId.isNotEmpty()) dao.getById(entry.entryId) else null)
            ?: dao.getByIdentity(
                endpoint = if (entry.host.contains(':')) "[${entry.host.lowercase(Locale.ROOT)}]:${entry.port}" else "${entry.host.lowercase(Locale.ROOT)}:${entry.port}",
                login = entry.login.lowercase(Locale.ROOT),
            )

        if (entity != null) {
            dao.updateToken(entity.entryId, null, null)
            Timber.d("S0018: forgot token for entryId=${hashId(entity.entryId)}")
        }
    }

    companion object {
        fun hashId(id: String): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(id.toByteArray(Charsets.UTF_8))
            return digest.take(4).joinToString("") { "%02x".format(it) }
        }
    }
}
