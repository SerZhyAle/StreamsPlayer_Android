package com.sza.streamsplayer.data.exchange

import com.sza.streamsplayer.data.exchange.session.DeviceExchangeAccountClient
import com.sza.streamsplayer.data.exchange.session.ExchangeProductVersion
import com.sza.streamsplayer.data.repository.StreamSourceRepository
import timber.log.Timber
import java.io.IOException
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages the exchange account entry lifecycle (S0018 Scope 3 and Scope 8):
 * - One entry per product (replacement on a new server)
 * - deviceId stability across re-enrolment
 * - Removal order: revoke own deviceId, forget token, delete entry, mark bound rows "not listed".
 * - Resilient when server is down (forgets locally, does not retry in background).
 */
@Singleton
class ExchangeAccountManager @Inject constructor(
    private val entryStore: EntryStore,
    private val secretStore: SecretStore,
    private val connector: ExchangeConnector,
    private val streamSourceRepository: StreamSourceRepository,
    private val watchRemoval: WatchExchangeLeaveCoordinator? = null,
    private val product: ExchangeProductVersion = ExchangeProductVersion.UNKNOWN,
) {
    var lastWatchRemoval: WatchExchangeLeaveResult = WatchExchangeLeaveResult()
        private set

    /**
     * Creates a new account entry, or replaces an existing one (Scope 3).
     * If an existing entry has the same (endpoint, login), it re-enrols it preserving deviceId.
     * If an existing entry has a different endpoint/login, it removes the old entry first (Scope 8).
     */
    suspend fun createOrReplaceEntry(
        endpoint: String,
        login: String,
        pin: String? = null,
        deviceName: String = "Android",
        label: String? = null,
    ): CreateEntryResult {
        val normalizedEndpoint = ExchangeAccountEntry.normalizeEndpoint(endpoint)
        val normalizedLogin = ExchangeAccountEntry.normalizeLogin(login)

        val existing = entryStore.getAccountEntry()
        if (existing != null) {
            val sameIdentity = existing.endpoint == normalizedEndpoint && existing.login == normalizedLogin
            if (sameIdentity) {
                // Re-enrolment of the same identity (Scope 2): reuse existing deviceId!
                val updated = existing.copy(
                    pin = pin,
                    pinnedPublicKey = existing.pinnedPublicKey.takeIf { pin == existing.pin },
                    label = label,
                    deviceName = deviceName,
                    state = ExchangeAccountState.Connected,
                )
                entryStore.saveAccountEntry(updated)
                Timber.d("S0018: re-enrolled entry deviceId=${hashId(updated.deviceId)}")
                return CreateEntryResult.Reenrolled(updated)
            } else {
                // Replace old entry (Scope 3): remove old entry first via removal order!
                Timber.d("S0018: replacing existing entry with new server $normalizedEndpoint")
                removeAccountEntry()
            }
        }

        val newEntry = ExchangeAccountEntry(
            endpoint = normalizedEndpoint,
            login = normalizedLogin,
            pin = pin,
            deviceName = deviceName,
            label = label,
            deviceId = DeviceIdGenerator.generate(),
            state = ExchangeAccountState.Connected,
        )
        entryStore.saveAccountEntry(newEntry)
        Timber.d("S0018: created new entry deviceId=${hashId(newEntry.deviceId)}")
        return CreateEntryResult.Created(newEntry)
    }

    /**
     * Removes the current account entry according to Scope 8:
     * In this order, each best effort:
     * 1. Close the held control stream (caller).
     * 2. Authenticate a separate stream and send `revoke` with its own `deviceId`, without `bye`.
     * 3. Forget the token
     * 4. Delete the entry
     * 5. Mark every row bound to it "not listed" without deleting any rows (Scope 7).
     *
     * Never depends on server being reachable: forgets locally on failure and does not retry.
     */
    suspend fun removeAccountEntry(): RemoveEntryResult {
        val entry = entryStore.getAccountEntry() ?: return RemoveEntryResult.Success
        val devEntry = entry.toDeviceExchangeAccountEntry()
        lastWatchRemoval = watchRemoval?.remove(devEntry) ?: WatchExchangeLeaveResult()
        if (watchRemoval != null) {
            Timber.d("S0027: paired watch is signed out together with this device")
        }
        val token = secretStore.token(devEntry)

        var serverUnreachable = false

        if (token != null) {
            try {
                val trust = ExchangeTrust.Verified(entry.pin, entry.pinnedPublicKey)
                connector.connect(entry.host, entry.port, trust).use { connection ->
                    connection.socket.soTimeout = 15_000
                    // 1. Send hello to authenticate stream for revoke
                    val hello = DeviceExchangeEnvelope.of(DeviceExchangeEnvelope.TYPE_HELLO) {
                        addProperty(DeviceExchangeAccountClient.KEY_DEVICE_ID, entry.deviceId)
                        addProperty(DeviceExchangeAccountClient.KEY_DEVICE_TOKEN, token)
                        addProperty("productVersion", product.name)
                    }
                    DeviceExchangeFrameCodec.write(connection.output, hello, DeviceExchangeFrameCodec.MAX_FRAME_BODY)
                    val welcome = DeviceExchangeFrameCodec.read(connection.input, DeviceExchangeFrameCodec.MAX_FRAME_BODY)

                    if (welcome?.type == DeviceExchangeEnvelope.TYPE_WELCOME) {
                        Timber.d("S0018: sending revoke own deviceId=${hashId(entry.deviceId)} on removal")
                        val revoke = DeviceExchangeEnvelope.of(DeviceExchangeEnvelope.TYPE_REVOKE) {
                            addProperty(DeviceExchangeAccountClient.KEY_DEVICE_ID, entry.deviceId)
                        }
                        DeviceExchangeFrameCodec.write(connection.output, revoke, DeviceExchangeFrameCodec.MAX_FRAME_BODY)
                    } else {
                        serverUnreachable = true
                    }
                }
            } catch (e: IOException) {
                Timber.d(e, "S0018: server unreachable during remove; proceeding locally")
                serverUnreachable = true
            } catch (e: Exception) {
                Timber.d(e, "S0018: exception during remove wire handshake; proceeding locally")
                serverUnreachable = true
            }
        } else {
            serverUnreachable = true
        }

        // 3. Forget the token
        secretStore.forget(devEntry)

        // 4. Delete the entry
        entryStore.removeEntry()

        // 5. Mark rows bound to this entry "not listed" (Scope 7)
        streamSourceRepository.markEntryRowsNotListed(entry.entryId)

        return if (serverUnreachable) {
            RemoveEntryResult.ServerUnreachable(
                "Old device stays in the account's list until another device revokes it"
            )
        } else {
            RemoveEntryResult.Success
        }
    }

    sealed interface CreateEntryResult {
        data class Created(val entry: ExchangeAccountEntry) : CreateEntryResult
        data class Reenrolled(val entry: ExchangeAccountEntry) : CreateEntryResult
    }

    sealed interface RemoveEntryResult {
        data object Success : RemoveEntryResult
        data class ServerUnreachable(val message: String) : RemoveEntryResult
    }

    companion object {
        fun hashId(id: String): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(id.toByteArray(Charsets.UTF_8))
            return digest.take(4).joinToString("") { "%02x".format(it) }
        }
    }
}
