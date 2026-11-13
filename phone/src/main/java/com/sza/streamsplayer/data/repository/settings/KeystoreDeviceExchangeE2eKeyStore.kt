package com.sza.streamsplayer.data.repository.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.sza.streamsplayer.data.exchange.security.KeyCipher
import com.sza.streamsplayer.data.exchange.session.DeviceExchangeE2eKeyStore
import com.sza.streamsplayer.data.security.e2e.E2eBytes
import com.sza.streamsplayer.data.security.e2e.E2eDeviceKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Device keys encrypted with the Keystore-backed [KeyCipher], in the same preferences as the device tokens,
 * which are excluded from backup and device transfer: a restored copy decrypts to nothing, so a key is never
 * carried to another device (item DF).
 */
@Singleton
class KeystoreDeviceExchangeE2eKeyStore internal constructor(
    private val dataStore: DataStore<Preferences>,
    private val encrypt: (String) -> String?,
    private val decrypt: (String?) -> String?,
) : DeviceExchangeE2eKeyStore {

    @Inject
    constructor(dataStore: DataStore<Preferences>, cipher: KeyCipher) : this(
        dataStore,
        { clear ->
            kotlin.runCatching {
                val sealed = cipher.encrypt(clear.toByteArray(Charsets.UTF_8))
                Base64.getEncoder().encodeToString(sealed.iv) + ":" +
                    Base64.getEncoder().encodeToString(sealed.ciphertext)
            }.getOrNull()
        },
        { sealed ->
            sealed?.let { value ->
                kotlin.runCatching {
                    val parts = value.split(':', limit = 2)
                    String(
                        cipher.decrypt(
                            Base64.getDecoder().decode(parts[1]),
                            Base64.getDecoder().decode(parts[0]),
                        ),
                        Charsets.UTF_8,
                    )
                }.getOrNull()
            }
        },
    )

    // Two enrollments of one id must not each make a key and race to store it.
    private val creation = Mutex()

    override suspend fun keyFor(deviceId: String): E2eDeviceKey? = creation.withLock {
        existing(deviceId) ?: create(deviceId)
    }

    override suspend fun existing(deviceId: String): E2eDeviceKey? =
        dataStore.data.first()[key(deviceId)]
            ?.let(decrypt)
            ?.takeUnless(String::isEmpty)
            ?.let { hex -> runCatching { E2eDeviceKey.fromPrivate(E2eBytes.fromHex(hex)) }.getOrNull() }

    override suspend fun forget(deviceId: String) {
        dataStore.edit { it.remove(key(deviceId)) }
    }

    private suspend fun create(deviceId: String): E2eDeviceKey? {
        val made = E2eDeviceKey.generate()
        val clear = E2eBytes.hex(made.exportPrivateForSecretStore())
        val encrypted = encrypt(clear)?.takeUnless(String::isEmpty)
        if (encrypted == null) {
            Timber.w("Exchange device key: the secret store refused the key, the entry cannot seal")
        } else {
            dataStore.edit { it[key(deviceId)] = encrypted }
        }
        return made.takeIf { encrypted != null }
    }

    // Hashed so the preference name does not spell out the device id.
    private fun key(deviceId: String): Preferences.Key<String> {
        val name = E2eBytes.hex(E2eBytes.sha256(E2eBytes.utf8(deviceId)).copyOf(KEY_HASH_BYTES))
        return stringPreferencesKey(KEY_PREFIX + name)
    }

    private companion object {
        const val KEY_PREFIX = "device_exchange_e2e_key_"
        const val KEY_HASH_BYTES = 8
    }
}
