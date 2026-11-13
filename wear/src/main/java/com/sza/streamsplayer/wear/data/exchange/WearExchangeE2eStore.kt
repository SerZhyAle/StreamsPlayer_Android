package com.sza.streamsplayer.wear.data.exchange

import android.content.SharedPreferences
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.sza.streamsplayer.data.exchange.security.KeyCipher
import com.sza.streamsplayer.data.exchange.session.DeviceExchangeE2eKeyStore
import com.sza.streamsplayer.data.security.e2e.E2eBytes
import com.sza.streamsplayer.data.security.e2e.E2eDeviceKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The watch's device keys and the peer keys it trusts (contract DEVICE-EXCHANGE items DF and DG), one set
 * per own `deviceId`, because a key lives exactly as long as its id.
 */
interface WearExchangeE2eStore : DeviceExchangeE2eKeyStore {
    suspend fun trusted(deviceId: String): Map<String, String>
    suspend fun saveTrusted(deviceId: String, trusted: Map<String, String>)

    /** The sender key the watch first saw for each grant of a guest entry (27 item DJ). */
    suspend fun grantSenders(deviceId: String): Map<String, String>
    suspend fun saveGrantSenders(deviceId: String, senders: Map<String, String>)
}

/**
 * The private key is encrypted with the Keystore-backed [KeyCipher] the device tokens use, in preferences no
 * backup carries (item DF); a key an older build kept in clear is re-written encrypted on first read, so the
 * watch keeps its `deviceId`. Peer keys are public and stay readable.
 */
@Singleton
class EncryptedWearExchangeE2eStore @Inject constructor(
    private val prefs: SharedPreferences,
    private val cipher: KeyCipher,
) : WearExchangeE2eStore {

    private val creation = Mutex()

    override suspend fun keyFor(deviceId: String): E2eDeviceKey? = creation.withLock {
        existing(deviceId) ?: create(deviceId)
    }

    override suspend fun existing(deviceId: String): E2eDeviceKey? = withContext(Dispatchers.IO) {
        prefs.getString(KEY_SEALED_PREFIX + deviceId, null)?.let(::unseal)?.let(::parse)
            ?: prefs.getString(KEY_PRIVATE_PREFIX + deviceId, null)?.let { hex ->
                parse(hex)?.also { migrate(deviceId, hex) }
            }
    }

    override suspend fun forget(deviceId: String) {
        withContext(Dispatchers.IO) {
            prefs.edit()
                .remove(KEY_SEALED_PREFIX + deviceId)
                .remove(KEY_PRIVATE_PREFIX + deviceId)
                .remove(KEY_TRUSTED_PREFIX + deviceId)
                .remove(KEY_GRANT_SENDERS_PREFIX + deviceId)
                .commit()
        }
    }

    override suspend fun trusted(deviceId: String): Map<String, String> = readMap(KEY_TRUSTED_PREFIX + deviceId)

    override suspend fun saveTrusted(deviceId: String, trusted: Map<String, String>) =
        writeMap(KEY_TRUSTED_PREFIX + deviceId, trusted)

    override suspend fun grantSenders(deviceId: String): Map<String, String> =
        readMap(KEY_GRANT_SENDERS_PREFIX + deviceId)

    override suspend fun saveGrantSenders(deviceId: String, senders: Map<String, String>) =
        writeMap(KEY_GRANT_SENDERS_PREFIX + deviceId, senders)

    private suspend fun readMap(name: String): Map<String, String> = withContext(Dispatchers.IO) {
        prefs.getString(name, null)?.let { json ->
            runCatching {
                JsonParser.parseString(json).asJsonObject.entrySet().associate { (id, kem) -> id to kem.asString }
            }.getOrNull()
        }.orEmpty()
    }

    private suspend fun writeMap(name: String, values: Map<String, String>) {
        val json = JsonObject()
        values.forEach { (id, kem) -> json.addProperty(id, kem) }
        withContext(Dispatchers.IO) { prefs.edit().putString(name, json.toString()).commit() }
    }

    private suspend fun create(deviceId: String): E2eDeviceKey? = withContext(Dispatchers.IO) {
        val made = E2eDeviceKey.generate()
        val sealed = seal(E2eBytes.hex(made.exportPrivateForSecretStore()))
        val kept = sealed != null && prefs.edit().putString(KEY_SEALED_PREFIX + deviceId, sealed).commit()
        if (!kept) Timber.w("Exchange device key: the encrypted store refused it, the entry cannot seal")
        made.takeIf { kept }
    }

    // A refused re-write keeps the clear entry, so the next read tries again and the key is never lost.
    private fun migrate(deviceId: String, hex: String) {
        val sealed = seal(hex)
        val moved = sealed != null && prefs.edit()
            .putString(KEY_SEALED_PREFIX + deviceId, sealed)
            .remove(KEY_PRIVATE_PREFIX + deviceId)
            .commit()
        if (!moved) Timber.w("Exchange device key: the encrypted store refused the move, it stays as it was")
    }

    private fun parse(hex: String): E2eDeviceKey? =
        runCatching { E2eDeviceKey.fromPrivate(E2eBytes.fromHex(hex)) }.getOrNull()

    private fun seal(clear: String): String? = runCatching {
        val bytes = clear.toByteArray(Charsets.UTF_8)
        val sealed = try { cipher.encrypt(bytes) } finally { bytes.fill(0) }
        Base64.getEncoder().encodeToString(sealed.iv) + ":" + Base64.getEncoder().encodeToString(sealed.ciphertext)
    }.getOrNull()

    private fun unseal(value: String): String? = runCatching {
        val parts = value.split(':', limit = 2)
        val clear = cipher.decrypt(Base64.getDecoder().decode(parts[1]), Base64.getDecoder().decode(parts[0]))
        try { String(clear, Charsets.UTF_8) } finally { clear.fill(0) }
    }.getOrNull()

    private companion object {
        const val KEY_SEALED_PREFIX = "exchange_e2e_key_v2/"
        const val KEY_PRIVATE_PREFIX = "exchange_e2e_key_v1/"
        const val KEY_TRUSTED_PREFIX = "exchange_e2e_trusted_v1/"
        const val KEY_GRANT_SENDERS_PREFIX = "exchange_e2e_grant_senders_v1/"
    }
}
