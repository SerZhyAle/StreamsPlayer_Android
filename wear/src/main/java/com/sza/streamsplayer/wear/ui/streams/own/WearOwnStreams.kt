package com.sza.streamsplayer.wear.ui.streams.own

import com.sza.streamsplayer.data.broadcast.BroadcastDescriptorDto
import com.sza.streamsplayer.data.exchange.directory.DeviceExchangeBroadcastRecord
import com.sza.streamsplayer.data.exchange.directory.DeviceExchangeDeviceRecord
import com.sza.streamsplayer.data.exchange.directory.DirectorySnapshot
import com.sza.streamsplayer.domain.model.ExchangeSealIssue
import com.sza.streamsplayer.wear.domain.model.WearStreamChannel

private typealias ParsedRecord = Pair<DeviceExchangeBroadcastRecord, BroadcastDescriptorDto?>

/** S0038: whether a device is known to be up, as its directory record says; never assumed. */
enum class WearLivePresence { ONLINE, OFFLINE, UNKNOWN }

/** S0038: what a person gets from a live broadcast. */
enum class WearLiveKind { AUDIO, VIDEO }

/** S0038: why the watch cannot play a live row; the row stays and says so. */
enum class WearLiveLimit {
    /** The watch plays audio broadcasts only. */
    VIDEO,

    /** A password broadcast opens on the phone. */
    PROTECTED,

    /** The descriptor is unreadable, of an unknown mode, or offers no endpoint the watch can use. */
    FORMAT,

    /** The broadcast's box did not open on this watch (DEVICE-EXCHANGE 27 item DR). */
    SEALED_FAILED,

    /** The watch holds no key of its own, so the broadcast's box cannot open here (27 item DP). */
    E2E_MISSING,
    ;

    companion object {
        fun of(issue: ExchangeSealIssue?): WearLiveLimit? = when (issue) {
            null -> null
            ExchangeSealIssue.E2E_MISSING -> E2E_MISSING
            ExchangeSealIssue.SEALED_FAILED, ExchangeSealIssue.KEY_CHANGED -> SEALED_FAILED
        }
    }
}

/**
 * One broadcast live on another device of the connected account. Identity is the account entry plus
 * [broadcastId] - never the device name, the device id alone, the address or the list position.
 */
data class WearLiveBroadcast(
    val entryId: String,
    val broadcastId: String,
    val deviceId: String,
    /** Null when the device record carries no name; the screen shows a localized fallback. */
    val deviceName: String?,
    /** Null when the record carries no title; the screen shows a localized fallback. */
    val title: String?,
    /** Null when the mode is one this product does not know. */
    val kind: WearLiveKind?,
    val presence: WearLivePresence,
    /** Null when the watch can play it. */
    val limit: WearLiveLimit?,
    /** The saved channel this broadcast is the live side of, if one was kept. */
    val saved: WearStreamChannel?,
    val descriptorJson: String,
) {
    val playable: Boolean get() = limit == null
}

data class WearLiveDevice(
    val deviceId: String,
    val deviceName: String?,
    val presence: WearLivePresence,
    val broadcasts: List<WearLiveBroadcast>,
)

/**
 * The live group of the Own category, already searched and ordered, and the ids of the saved channels it already
 * shows - those leave the saved group, so a channel appears once.
 */
data class WearOwnLiveView(
    val devices: List<WearLiveDevice> = emptyList(),
    val shownSavedIds: Set<String> = emptySet(),
) {
    val isEmpty: Boolean get() = devices.isEmpty()
}

/**
 * S0038: how the watch's Own category joins what is saved here with what the account's devices broadcast now. The
 * join is a presentation: it writes nothing, so a broadcast ending removes a live row and never a saved channel.
 */
object WearOwnStreamsProjector {

    /**
     * @param saved every personal channel of the watch, before search - the Saved indicator of a live row does not
     * depend on it.
     * @param parse reads a descriptor as the receiver does; null for one it cannot read.
     */
    fun project(
        entryId: String,
        directory: DirectorySnapshot?,
        saved: List<WearStreamChannel>,
        parse: (String) -> BroadcastDescriptorDto?,
        query: String,
    ): WearOwnLiveView {
        val devices = directory?.devices.orEmpty().associateBy { it.deviceId }
        val live = directory?.broadcasts.orEmpty().newestPerSource(parse)
            .map { (record, dto) -> record.toLive(entryId, devices[record.deviceId], dto, saved) }
        val text = query.trim()
        val visible = live.filter { matchesQuery(it, text) }
        val groups = visible
            .groupBy { it.deviceId }
            .map { (deviceId, rows) ->
                val first = rows.first()
                WearLiveDevice(deviceId, first.deviceName, first.presence, rows.sortedWith(ROW_ORDER))
            }
            .sortedWith(
                compareBy<WearLiveDevice, String>(String.CASE_INSENSITIVE_ORDER) { it.deviceName.orEmpty() }
                    .thenBy { it.deviceId },
            )
        return WearOwnLiveView(devices = groups, shownSavedIds = visible.mapNotNullTo(HashSet()) { it.saved?.id })
    }

    private fun matchesQuery(row: WearLiveBroadcast, query: String): Boolean =
        query.isEmpty() ||
            row.title?.contains(query, ignoreCase = true) == true ||
            row.deviceName?.contains(query, ignoreCase = true) == true ||
            row.saved?.name?.contains(query, ignoreCase = true) == true

    /**
     * A source that restarted leaves its previous record until that expires; both carry the same `sourceId`
     * (LIVE-BROADCAST 3.1, amendment G), so the later `updatedAt` stands for it. Records with no `sourceId`, or
     * from different devices, are never joined - equal titles or addresses authorize nothing.
     */
    private fun List<DeviceExchangeBroadcastRecord>.newestPerSource(
        parse: (String) -> BroadcastDescriptorDto?,
    ): List<ParsedRecord> {
        val parsed: List<ParsedRecord> = map { it to parse(it.descriptorJson) }
        val newest = HashMap<Pair<String, String>, DeviceExchangeBroadcastRecord>()
        for ((record, dto) in parsed) {
            val key = dto?.sourceId?.let { record.deviceId to it } ?: continue
            val held = newest[key]
            if (held == null || record.updatedAtMs > held.updatedAtMs ||
                (record.updatedAtMs == held.updatedAtMs && record.broadcastId > held.broadcastId)
            ) {
                newest[key] = record
            }
        }
        return parsed.filter { (record, dto) ->
            val sourceId = dto?.sourceId ?: return@filter true
            newest[record.deviceId to sourceId]?.broadcastId == record.broadcastId
        }
    }

    private fun DeviceExchangeBroadcastRecord.toLive(
        entryId: String,
        device: DeviceExchangeDeviceRecord?,
        dto: BroadcastDescriptorDto?,
        saved: List<WearStreamChannel>,
    ): WearLiveBroadcast = WearLiveBroadcast(
        entryId = entryId,
        broadcastId = broadcastId,
        deviceId = deviceId,
        deviceName = device?.deviceName?.takeIf { it.isNotBlank() },
        title = title?.takeIf { it.isNotBlank() },
        kind = kindOf(mode),
        presence = when {
            device == null -> WearLivePresence.UNKNOWN
            device.isOnline -> WearLivePresence.ONLINE
            device.presence == "offline" -> WearLivePresence.OFFLINE
            else -> WearLivePresence.UNKNOWN
        },
        limit = WearLiveLimit.of(sealIssue) ?: limitOf(mode, dto),
        saved = savedFor(entryId, broadcastId, dto, saved),
        descriptorJson = descriptorJson,
    )

    /**
     * What stops the watch from playing a broadcast, by the same rules its resolver applies: schema 1, audio only,
     * no password, and at least one endpoint over a transport the watch carries.
     */
    fun limitOf(mode: String, dto: BroadcastDescriptorDto?): WearLiveLimit? = when {
        kindOf(mode) == WearLiveKind.VIDEO -> WearLiveLimit.VIDEO
        dto == null || mode != AUDIO_ONLY || dto.mode != AUDIO_ONLY || dto.schemaVersion != 1 -> WearLiveLimit.FORMAT
        dto.auth != null -> WearLiveLimit.PROTECTED
        dto.getEffectiveEndpoints().none { it.transport in PLAYABLE_TRANSPORTS } -> WearLiveLimit.FORMAT
        else -> null
    }

    /**
     * The saved channel of a broadcast: the one bound to this exact entry and broadcast, else the one that holds
     * the same opaque `sourceId` and is not bound to another account. The second rule is what lets a restarted
     * source (new broadcast id, maybe a new address) find the channel the person kept.
     */
    internal fun savedFor(
        entryId: String,
        broadcastId: String,
        dto: BroadcastDescriptorDto?,
        saved: List<WearStreamChannel>,
    ): WearStreamChannel? =
        saved.firstOrNull { it.exchangeEntryId == entryId && it.exchangeBroadcastId == broadcastId }
            ?: dto?.sourceId?.let { sourceId ->
                saved.firstOrNull {
                    it.broadcastSourceId == sourceId && (it.exchangeEntryId == null || it.exchangeEntryId == entryId)
                }
            }

    fun kindOf(mode: String): WearLiveKind? = when (mode) {
        AUDIO_ONLY -> WearLiveKind.AUDIO
        "VIDEO_AUDIO", "VIDEO_ONLY" -> WearLiveKind.VIDEO
        else -> null
    }

    /** Stable on purpose: a background update must not move rows under the crown. */
    private val ROW_ORDER: Comparator<WearLiveBroadcast> =
        compareBy<WearLiveBroadcast, String>(String.CASE_INSENSITIVE_ORDER) { it.title.orEmpty() }.thenBy { it.broadcastId }

    private const val AUDIO_ONLY = "AUDIO_ONLY"
    private val PLAYABLE_TRANSPORTS = setOf("HTTP", "RELAY", "TUNNEL")
}
