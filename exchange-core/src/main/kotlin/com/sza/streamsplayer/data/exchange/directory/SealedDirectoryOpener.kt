package com.sza.streamsplayer.data.exchange.directory

import com.sza.streamsplayer.data.exchange.session.DeviceExchangeE2eKeyStore
import com.sza.streamsplayer.data.security.e2e.E2ePeer
import com.sza.streamsplayer.data.security.e2e.E2eRecipients
import com.sza.streamsplayer.data.security.e2e.E2eSelf
import javax.inject.Inject

/**
 * The device keys this device trusts, per own `deviceId` (contract DEVICE-EXCHANGE item DG), and the first sender
 * key of each grant (item DJ). The phone and the watch each keep them in their own store.
 */
interface ExchangeE2eTrust {
    suspend fun trusted(ownDeviceId: String): Map<String, String>

    /** Adds the keys of [firstSeen] that are not trusted yet; a held key is never replaced here. */
    suspend fun learn(ownDeviceId: String, firstSeen: Map<String, String>)

    /** Keeps only the keys of [listed] devices: one missing from the whole list was removed (`revoke`). */
    suspend fun retain(ownDeviceId: String, listed: Set<String>)

    suspend fun grantSenders(ownDeviceId: String): Map<String, String>

    suspend fun learnGrantSenders(ownDeviceId: String, firstSeen: Map<String, String>)
}

/** Turns the directory the server delivered into the one this device lists (S0032). */
fun interface DirectoryOpener {
    /** [snapshot] is the whole directory, the own device included. */
    suspend fun open(ownDeviceId: String, snapshot: DirectorySnapshot): DirectorySnapshot

    companion object {
        val NONE = DirectoryOpener { _, snapshot -> snapshot }
    }
}

/**
 * Opens the broadcast boxes of a directory with this device's key and the keys it trusts (27 items DG, DH, DK),
 * learning first-seen keys and dropping the keys of removed devices on the way, for the phone and the watch alike.
 */
class SealedDirectoryOpener @Inject constructor(
    private val keys: DeviceExchangeE2eKeyStore,
    private val trust: ExchangeE2eTrust,
) : DirectoryOpener {

    private val opener = ExchangeBroadcastOpener()

    override suspend fun open(ownDeviceId: String, snapshot: DirectorySnapshot): DirectorySnapshot {
        val peers = snapshot.devices.map { it.toPeer() }
        // Only a listing that names this device is the whole list; anything less never drops a kept key.
        if (peers.any { it.deviceId == ownDeviceId }) trust.retain(ownDeviceId, peers.mapTo(HashSet()) { it.deviceId })
        trust.learn(ownDeviceId, E2eRecipients.learned(ownDeviceId, peers, trust.trusted(ownDeviceId)))
        val grants = ExchangeBroadcastOpener.GrantTrust(trust.grantSenders(ownDeviceId))
        // This device's own broadcasts are never addressed to it and are not listed here (9 item 1).
        val (own, others) = snapshot.broadcasts.partition { it.deviceId == ownDeviceId }
        val broadcasts = own + opener.open(sealing(ownDeviceId), others, grants)
        trust.learnGrantSenders(ownDeviceId, grants.learned)
        return snapshot.copy(broadcasts = broadcasts)
    }

    /** A broadcast offered by `cast` (7.8) opens with the keys this device already trusts. */
    suspend fun openCast(ownDeviceId: String, record: DeviceExchangeBroadcastRecord): DeviceExchangeBroadcastRecord =
        opener.openBroadcast(sealing(ownDeviceId), record)

    /** [openCast] for the `broadcast` member of a `cast` offer: its descriptor with the box's keys written in. */
    suspend fun openCast(ownDeviceId: String, broadcastId: String, descriptorJson: String, sealed: String?): String =
        openCast(
            ownDeviceId,
            DeviceExchangeBroadcastRecord(
                broadcastId = broadcastId,
                deviceId = "",
                title = null,
                mode = "",
                startedAtMs = null,
                updatedAtMs = 0L,
                descriptorJson = descriptorJson,
                writtenAtMs = 0L,
                ttlSeconds = 0L,
                sealed = sealed,
            ),
        ).descriptorJson

    private suspend fun sealing(ownDeviceId: String): ExchangeBroadcastOpener.Sealing {
        val trusted = trust.trusted(ownDeviceId)
        val self = keys.existing(ownDeviceId)?.let { E2eSelf(ownDeviceId, it) }
        return ExchangeBroadcastOpener.Sealing(self) { from, fromKey -> E2eRecipients.trusts(trusted, from, fromKey) }
    }

    private fun DeviceExchangeDeviceRecord.toPeer() = E2ePeer(
        deviceId = deviceId,
        kem = e2eKem,
        capable = DeviceExchangeDeviceRecord.CAPABILITY_E2E_V1 in capabilities,
        updatedAtMs = writtenAtMs,
    )
}
