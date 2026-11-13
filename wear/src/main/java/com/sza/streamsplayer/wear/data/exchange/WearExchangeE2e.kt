package com.sza.streamsplayer.wear.data.exchange

import com.sza.streamsplayer.data.exchange.directory.DeviceExchangeDeviceRecord
import com.sza.streamsplayer.data.exchange.directory.ExchangeE2eTrust
import com.sza.streamsplayer.data.security.e2e.E2ePeer
import com.sza.streamsplayer.data.security.e2e.E2eRecipient
import com.sza.streamsplayer.data.security.e2e.E2eRecipients
import com.sza.streamsplayer.data.security.e2e.E2eSelf
import javax.inject.Inject
import javax.inject.Singleton

/** Who a box of one watch entry goes to, and the check a box's sender must pass (contract items DG and DH). */
class WearSealing(
    /** The watch as a sender and reader; null for an entry enrolled without a key, which seals nothing. */
    val self: E2eSelf?,
    val recipients: List<E2eRecipient>,
    private val trusted: Map<String, String>,
) {
    fun trusts(from: String, fromKey: ByteArray): Boolean = E2eRecipients.trusts(trusted, from, fromKey)
}

/**
 * The watch's side of the trust rules, the same shared rules the phone applies.
 */
@Singleton
class WearExchangeE2e @Inject constructor(
    private val store: WearExchangeE2eStore,
) {

    suspend fun sealing(ownDeviceId: String, devices: Collection<DeviceExchangeDeviceRecord>): WearSealing {
        val peers = devices.map { dev ->
            E2ePeer(
                deviceId = dev.deviceId,
                kem = dev.e2eKem,
                capable = dev.capabilities.contains("e2e-v1"),
                updatedAtMs = dev.writtenAtMs,
            )
        }
        val listed = peers.map { it.deviceId }.toSet()
        val held = store.trusted(ownDeviceId).filterKeys { it in listed }
        val trusted = held + E2eRecipients.learned(ownDeviceId, peers, held)
        if (trusted != store.trusted(ownDeviceId)) store.saveTrusted(ownDeviceId, trusted)
        val self = store.existing(ownDeviceId)?.let { E2eSelf(ownDeviceId, it) }
        val recipients = if (self == null) emptyList() else E2eRecipients.recipients(ownDeviceId, peers, trusted)
        return WearSealing(self, recipients, trusted)
    }
}

/** The watch's trusted keys for the shared directory opener (27 items DG and DJ), kept in [WearExchangeE2eStore]. */
@Singleton
class WearExchangeE2eTrust @Inject constructor(
    private val store: WearExchangeE2eStore,
) : ExchangeE2eTrust {

    override suspend fun trusted(ownDeviceId: String): Map<String, String> = store.trusted(ownDeviceId)

    override suspend fun learn(ownDeviceId: String, firstSeen: Map<String, String>) {
        if (firstSeen.isEmpty()) return
        val held = store.trusted(ownDeviceId)
        store.saveTrusted(ownDeviceId, firstSeen.filterKeys { it !in held } + held)
    }

    override suspend fun retain(ownDeviceId: String, listed: Set<String>) {
        val held = store.trusted(ownDeviceId)
        if (held.keys.all { it in listed }) return
        store.saveTrusted(ownDeviceId, held.filterKeys { it in listed })
    }

    override suspend fun grantSenders(ownDeviceId: String): Map<String, String> = store.grantSenders(ownDeviceId)

    override suspend fun learnGrantSenders(ownDeviceId: String, firstSeen: Map<String, String>) {
        if (firstSeen.isEmpty()) return
        val held = store.grantSenders(ownDeviceId)
        store.saveGrantSenders(ownDeviceId, firstSeen.filterKeys { it !in held } + held)
    }
}
