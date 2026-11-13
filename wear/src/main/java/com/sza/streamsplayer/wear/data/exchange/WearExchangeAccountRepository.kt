package com.sza.streamsplayer.wear.data.exchange

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.sza.streamsplayer.data.exchange.DeviceIdGenerator
import com.sza.streamsplayer.data.exchange.ExchangeAccountEntry
import com.sza.streamsplayer.data.exchange.ExchangeAccountState
import com.sza.streamsplayer.data.exchange.session.DeviceExchangeAccountClient
import com.sza.streamsplayer.data.exchange.session.DeviceExchangeControlStream
import com.sza.streamsplayer.data.exchange.session.DeviceExchangeCredential
import com.sza.streamsplayer.data.exchange.session.DeviceExchangeEnrollResult
import com.sza.streamsplayer.data.exchange.session.DeviceExchangeEnrollment
import com.sza.streamsplayer.wear.BuildConfig
import com.sza.streamsplayer.wear.domain.usecase.GetWatchDisplayNameUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

data class WearExchangeJoin(
    val host: String, val port: Int, val login: String,
    val pinnedCertificate: String?, val pairingCode: String, val pinnedKey: String? = null,
) {
    override fun toString() = "WearExchangeJoin(***)"
}

data class WearExchangeRemoval(val forgotten: Boolean, val revoked: Boolean)

/** Donor account lifecycle, using the one-account shared core and the watch's own Keystore store. */
@Singleton
class WearExchangeAccountRepository @Inject constructor(
    private val client: DeviceExchangeAccountClient,
    private val store: WearExchangeEntryStore,
    private val control: DeviceExchangeControlStream,
    private val displayName: GetWatchDisplayNameUseCase,
    private val publisher: com.sza.streamsplayer.data.exchange.publish.BroadcastExchangePublisher? = null,
) {
    private val changes = Mutex()
    val entries = store.entries

    suspend fun enroll(join: WearExchangeJoin): DeviceExchangeEnrollResult = withContext(Dispatchers.IO) {
        changes.withLock {
            control.setEntry(null)
            Timber.d("S0022: watch enroll started")
            val old = store.getAccountEntry()
            val endpoint = if (join.host.contains(':')) "[${join.host}]:${join.port}" else "${join.host}:${join.port}"
            val identity = "${ExchangeAccountEntry.normalizeEndpoint(endpoint)}/${ExchangeAccountEntry.normalizeLogin(join.login)}"
            if (old != null && old.identity != identity) removeLocked(old)
            val previous = old?.takeIf { it.identity == identity }
            var candidate = (previous ?: ExchangeAccountEntry(endpoint = endpoint, login = join.login)).copy(
                endpoint = endpoint, login = ExchangeAccountEntry.normalizeLogin(join.login),
                pin = join.pinnedCertificate, pinnedPublicKey = join.pinnedKey,
                deviceName = displayName(), state = ExchangeAccountState.Connected, signInReason = null,
            )
            val declaration = enrollment(candidate.deviceName, BuildConfig.VERSION_NAME)
            var answer = client.enroll(candidate.toDeviceExchangeAccountEntry(),
                DeviceExchangeCredential.PairingCode(join.pairingCode), declaration)
            if (previous == null && answer is DeviceExchangeEnrollResult.Refused && answer.reason == "id-collision") {
                candidate = candidate.copy(deviceId = DeviceIdGenerator.generate())
                answer = client.enroll(candidate.toDeviceExchangeAccountEntry(),
                    DeviceExchangeCredential.PairingCode(join.pairingCode), declaration)
            }
            if (answer is DeviceExchangeEnrollResult.Enrolled) {
                val recorded = store.saveAccountEntry(candidate.copy(accountKind = answer.welcome.accountKind))
                if (!recorded) {
                    store.forget(candidate.toDeviceExchangeAccountEntry())
                    return@withLock DeviceExchangeEnrollResult.Refused(DeviceExchangeEnrollResult.REASON_TOKEN_NOT_STORED)
                }
                control.setEntry(candidate.toDeviceExchangeAccountEntry())
                Timber.d("Watch exchange enrollment stored")
            } else if (previous?.state == ExchangeAccountState.Connected) {
                control.setEntry(previous.toDeviceExchangeAccountEntry())
            }
            answer
        }
    }

    suspend fun deviceId(): String? = store.getAccountEntry()?.deviceId

    /** Removes only the device named by the phone, so a delayed leave cannot remove a newer account. */
    suspend fun remove(deviceId: String): Boolean = removeWithResult(deviceId).forgotten

    suspend fun removeWithResult(deviceId: String): WearExchangeRemoval = withContext(Dispatchers.IO + NonCancellable) {
        changes.withLock {
            val entry = store.getAccountEntry()?.takeIf { it.deviceId == deviceId }
                ?: return@withLock WearExchangeRemoval(false, false)
            WearExchangeRemoval(forgotten = true, revoked = removeLocked(entry))
        }
    }

    private suspend fun removeLocked(entry: ExchangeAccountEntry): Boolean {
        publisher?.endAndAwait()
        control.setEntry(null)
        return try { client.revoke(entry.toDeviceExchangeAccountEntry()); true }
        catch (_: IOException) { Timber.i("Watch exchange removed locally; server was unavailable"); false }
        finally { store.removeEntry() }
    }

    companion object {
        fun enrollment(name: String, version: String) = DeviceExchangeEnrollment(
            deviceName = name, productVersion = version, product = "streamsplayer-wear", platform = "wearos",
            roles = listOf("broadcaster", "receiver"),
            receiver = JsonObject().apply {
                add("modes", JsonArray().apply { add("AUDIO_ONLY") })
                add("transports", JsonArray().apply { TRANSPORTS.forEach(::add) })
                add("plays", JsonArray().apply { TRANSPORTS.forEach { transport ->
                    add(JsonObject().apply { addProperty("mode", "AUDIO_ONLY"); addProperty("transport", transport) })
                } })
            },
        )
        private val TRANSPORTS = listOf("HTTP", "RELAY", "TUNNEL")
    }
}
