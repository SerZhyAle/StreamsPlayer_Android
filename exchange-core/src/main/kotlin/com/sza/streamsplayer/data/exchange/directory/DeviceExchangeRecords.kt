package com.sza.streamsplayer.data.exchange.directory

import com.google.gson.JsonElement
import com.sza.streamsplayer.domain.model.ExchangeSealIssue

/**
 * One record of contract DEVICE-EXCHANGE section 6 and section 8.
 * [writtenAtMs] and [ttlSeconds] come from the section 8.1 wrapper and are shared by
 * every record type; the body members are those of sections 6.1, 6.2, 6.3 and 8.3.
 */
sealed interface DeviceExchangeRecord {
    val writtenAtMs: Long
    val ttlSeconds: Long
}

typealias SftpRendezvousRecord = DeviceExchangeRecord

/** DEVICE-EXCHANGE 6.1: who a device is and whether it is up right now. */
data class DeviceExchangeDeviceRecord(
    val deviceId: String,
    val deviceName: String,
    val product: String,
    val productVersion: String?,
    val platform: String?,
    val roles: List<String>,
    val presence: String,
    val updatedAtMs: Long,
    override val writtenAtMs: Long,
    override val ttlSeconds: Long,
    val receiver: DeviceExchangeReceiverDeclaration? = null,
    /** 17 item AG and 27 item DF: what the device declared at `enroll`. */
    val capabilities: List<String> = emptyList(),
    /** 27 item DF: `kem` of the device's published `e2eKey`, null when it has none or one of an unknown version. */
    val e2eKem: String? = null,
) : DeviceExchangeRecord {

    val isOnline: Boolean get() = presence == PRESENCE_ONLINE

    /** Holds the capability `e2e-v1` and a key, the two things a recipient needs besides trust (item DP). */
    val canReceiveSealed: Boolean get() = CAPABILITY_E2E_V1 in capabilities && e2eKem != null

    companion object {
        const val CAPABILITY_E2E_V1 = "e2e-v1"
        const val PRODUCT_FMS_ANDROID = "fms-android"
        const val PRODUCT_STREAMSPLAYER_ANDROID = "streamsplayer-android"
        const val PRODUCT_STREAMSPLAYER_WINDOWS = "streamsplayer-windows"
        const val PLATFORM_ANDROID = "android"
        const val ROLE_RESOURCE_PRODUCER = "resource-producer"
        const val ROLE_RESOURCE_CONSUMER = "resource-consumer"
        const val ROLE_BROADCASTER = "broadcaster"
        const val ROLE_RECEIVER = "receiver"
        const val PRESENCE_ONLINE = "online"
        const val PRESENCE_OFFLINE = "offline"
    }
}

typealias SftpRendezvousDevice = DeviceExchangeDeviceRecord

/**
 * DEVICE-EXCHANGE 6.1 with amendment E: what a receiver plays. When [plays] is present a broadcaster covers
 * an endpoint only if its pair is listed; [modes] and [transports] stay the summary an older reader uses.
 */
data class DeviceExchangeReceiverDeclaration(
    val modes: List<String>,
    val transports: List<String>,
    val plays: List<DeviceExchangePlayPair>? = null,
)

typealias SftpRendezvousReceiver = DeviceExchangeReceiverDeclaration

/** One `{mode, transport}` pair of [DeviceExchangeReceiverDeclaration.plays]. */
data class DeviceExchangePlayPair(val mode: String, val transport: String)

typealias SftpRendezvousPlayPair = DeviceExchangePlayPair

/**
 * DEVICE-EXCHANGE 6.2 for kind `sftp-share`. [descriptor] is an `FMSSFTP1` or `FMSSFTP2` code and carries
 * the share's password by design, so it is never logged. It is null when the producer has not opted in to
 * putting `access` on Drive (section 15 item X): such a share is listed but cannot be attached from the
 * record alone. [shareId] is the outsider capability of 5.4 and stays out of logs.
 */
data class DeviceExchangeResourceRecord(
    val resourceId: String,
    val deviceId: String,
    val kind: String,
    val name: String,
    val descriptor: String?,
    val updatedAtMs: Long,
    override val writtenAtMs: Long,
    override val ttlSeconds: Long,
    val shareId: String? = null,
    val publicPort: Int? = null,
    val root: String? = null,
    val presence: String? = null,
    /** Retained for explicit denial; this product declares no pin-v1 or resource-consumer capability. */
    val protection: JsonElement? = null,
) : DeviceExchangeRecord {

    val isLocked: Boolean get() = protection != null && !protection.isJsonNull
    val denialReason: String? get() = if (isLocked) "protection-unsupported" else null

    override fun toString(): String =
        "DeviceExchangeResourceRecord(resourceId=$resourceId, deviceId=$deviceId, kind=$kind, name=$name)"

    companion object {
        const val KIND_SFTP_SHARE = "sftp-share"
    }
}

typealias SftpRendezvousResource = DeviceExchangeResourceRecord

/**
 * DEVICE-EXCHANGE 6.3: a broadcast live right now. [descriptorJson] is the `LIVE-BROADCAST` descriptor as
 * one compact JSON object, kept verbatim so a receiver imports exactly what the broadcaster wrote. The
 * broadcast id is the capability of the secret-link model, so neither it nor the descriptor is logged.
 */
data class DeviceExchangeBroadcastRecord(
    val broadcastId: String,
    val deviceId: String,
    val title: String?,
    val mode: String,
    val startedAtMs: Long?,
    val updatedAtMs: Long,
    val descriptorJson: String,
    override val writtenAtMs: Long,
    override val ttlSeconds: Long,
    /** 27 item DK: the record's box as compact JSON, holding `relayKey`, `tunnelPin` and `credentials`. */
    val sealed: String? = null,
    /** 20 item BD: the grant that lists this broadcast to a guest; its box's context is `grant:<grantId>` (DJ). */
    val grantId: String? = null,
    /** Set by the receiver only: why the box did not open on this device; null when it opened or there was none. */
    val sealIssue: ExchangeSealIssue? = null,
) : DeviceExchangeRecord {

    override fun toString(): String = "DeviceExchangeBroadcastRecord(deviceId=$deviceId, mode=$mode)"
}

typealias SftpRendezvousBroadcast = DeviceExchangeBroadcastRecord

/**
 * DEVICE-EXCHANGE 8.3: a request addressed to the device [toDeviceId].
 */
data class DeviceExchangeRequestRecord(
    val toDeviceId: String,
    val action: String,
    val fromDeviceId: String?,
    override val writtenAtMs: Long,
    override val ttlSeconds: Long,
) : DeviceExchangeRecord {

    companion object {
        const val ACTION_ANNOUNCE = "announce"
    }
}

typealias SftpRendezvousRequest = DeviceExchangeRequestRecord
