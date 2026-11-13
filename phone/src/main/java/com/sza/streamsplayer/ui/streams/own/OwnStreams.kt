package com.sza.streamsplayer.ui.streams.own

import com.sza.streamsplayer.data.broadcast.BroadcastDescriptorDto
import com.sza.streamsplayer.data.exchange.ExchangeReceiverDeclaration
import com.sza.streamsplayer.data.exchange.directory.DeviceExchangeBroadcastRecord
import com.sza.streamsplayer.data.exchange.directory.DirectorySnapshot
import com.sza.streamsplayer.data.local.db.StreamSourceEntity

private typealias ParsedRecord = Pair<DeviceExchangeBroadcastRecord, BroadcastDescriptorDto?>

/** S0038: whether a device is known to be up, as its directory record says; never assumed. */
enum class LivePresence { ONLINE, OFFLINE, UNKNOWN }

/** S0038: what a person gets from a live broadcast. */
enum class LiveKind { AUDIO, VIDEO }

/**
 * One broadcast live on another device of the connected account. Identity is the account entry plus
 * [broadcastId] - never the device name, the device id alone, the address or the list position.
 */
data class LiveBroadcast(
    val entryId: String,
    val broadcastId: String,
    val deviceId: String,
    /** Null when the device record carries no name; the screen shows a localized fallback. */
    val deviceName: String?,
    /** Null when the record carries no title; the screen shows a localized fallback. */
    val title: String?,
    /** Null when the mode is one this product does not know. */
    val kind: LiveKind?,
    val presence: LivePresence,
    /** False for a mode or descriptor this receiver cannot play; the row stays and says why. */
    val playable: Boolean,
    /** The saved channel this broadcast is the live side of, if one was kept. */
    val saved: StreamSourceEntity?,
    val descriptorJson: String,
    val updatedAtMs: Long,
)

data class LiveDevice(
    val deviceId: String,
    val deviceName: String?,
    val presence: LivePresence,
    val broadcasts: List<LiveBroadcast>,
)

/** The two groups of the Own category, already filtered and ordered. */
data class OwnStreamsView(
    val live: List<LiveDevice> = emptyList(),
    val saved: List<StreamSourceEntity> = emptyList(),
) {
    val isEmpty: Boolean get() = live.isEmpty() && saved.isEmpty()
}

/**
 * S0038: how the Own category joins what is saved here with what the account's devices broadcast now. The join
 * is a presentation: it writes nothing, so a broadcast ending removes a live row and never a saved channel.
 */
object OwnStreamsProjector {

    /**
     * Membership follows provenance, not transport: a row the person entered by hand, or kept from a
     * broadcast (it names its broadcasting source or its exchange binding). A catalog channel or a playlist
     * import stays out even though its origin may read IMPORTED.
     */
    fun isSavedOwn(source: StreamSourceEntity): Boolean =
        source.sourceOrigin == ORIGIN_MANUAL || source.sourceDeviceId != null || source.exchangeBroadcastId != null

    /**
     * @param saved every saved Own channel, before search and filters - the Saved indicator of a live row does
     * not depend on them.
     * @param parse reads a descriptor as the receiver does; null for one it cannot read.
     * @param arrangeSaved applies the person's search, filters and order to the saved channels that stand alone;
     * pin order of the saved group is whatever it returns.
     */
    fun project(
        entryId: String,
        directory: DirectorySnapshot?,
        saved: List<StreamSourceEntity>,
        parse: (String) -> BroadcastDescriptorDto?,
        query: String,
        pinnedOnly: Boolean,
        arrangeSaved: (List<StreamSourceEntity>) -> List<StreamSourceEntity>,
    ): OwnStreamsView {
        val devices = directory?.devices.orEmpty().associateBy { it.deviceId }
        val live = directory?.broadcasts.orEmpty().newestPerSource(parse)
            .map { (record, dto) -> record.toLive(entryId, devices[record.deviceId], dto, saved) }

        val matchedIds = live.mapNotNull { it.saved?.id }.toSet()
        val text = query.trim()
        val visibleLive = live.filter { row ->
            (!pinnedOnly || row.saved?.pinned == true) && matchesQuery(row, text)
        }
        val groups = visibleLive
            .groupBy { it.deviceId }
            .map { (deviceId, rows) ->
                val first = rows.first()
                LiveDevice(
                    deviceId = deviceId,
                    deviceName = first.deviceName,
                    presence = first.presence,
                    broadcasts = rows.sortedWith(ROW_ORDER),
                )
            }
            .sortedWith(compareBy<LiveDevice, String>(String.CASE_INSENSITIVE_ORDER) { it.deviceName.orEmpty() }.thenBy { it.deviceId })
        return OwnStreamsView(live = groups, saved = arrangeSaved(saved.filter { it.id !in matchedIds }))
    }

    private fun matchesQuery(row: LiveBroadcast, query: String): Boolean =
        query.isEmpty() ||
            row.title?.contains(query, ignoreCase = true) == true ||
            row.deviceName?.contains(query, ignoreCase = true) == true ||
            row.saved?.title?.contains(query, ignoreCase = true) == true

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
        device: com.sza.streamsplayer.data.exchange.directory.DeviceExchangeDeviceRecord?,
        dto: BroadcastDescriptorDto?,
        saved: List<StreamSourceEntity>,
    ): LiveBroadcast = LiveBroadcast(
        entryId = entryId,
        broadcastId = broadcastId,
        deviceId = deviceId,
        deviceName = device?.deviceName?.takeIf { it.isNotBlank() },
        title = title?.takeIf { it.isNotBlank() },
        kind = kindOf(mode),
        presence = when {
            device == null -> LivePresence.UNKNOWN
            device.isOnline -> LivePresence.ONLINE
            device.presence == "offline" -> LivePresence.OFFLINE
            else -> LivePresence.UNKNOWN
        },
        playable = dto != null && sealIssue == null && ExchangeReceiverDeclaration.modes.any { it == mode } &&
            ExchangeReceiverDeclaration.canPlay(dto),
        saved = savedFor(entryId, broadcastId, dto, saved),
        descriptorJson = descriptorJson,
        updatedAtMs = updatedAtMs,
    )

    /**
     * The saved channel of a broadcast: the one bound to this exact entry and broadcast, else the one that holds
     * the same opaque `sourceId` and is not bound to another account. The second rule is what lets a restarted
     * source (new broadcast id, maybe a new address) find the channel the person kept.
     */
    internal fun savedFor(
        entryId: String,
        broadcastId: String,
        dto: BroadcastDescriptorDto?,
        saved: List<StreamSourceEntity>,
    ): StreamSourceEntity? =
        saved.firstOrNull { it.exchangeEntryId == entryId && it.exchangeBroadcastId == broadcastId }
            ?: dto?.sourceId?.let { sourceId ->
                saved.firstOrNull {
                    it.sourceDeviceId == sourceId && (it.exchangeEntryId == null || it.exchangeEntryId == entryId)
                }
            }

    fun kindOf(mode: String): LiveKind? = when (mode) {
        "AUDIO_ONLY" -> LiveKind.AUDIO
        "VIDEO_AUDIO", "VIDEO_ONLY" -> LiveKind.VIDEO
        else -> null
    }

    /** Stable on purpose: a background update must not reshuffle rows under a focused one. */
    private val ROW_ORDER: Comparator<LiveBroadcast> =
        compareBy<LiveBroadcast, String>(String.CASE_INSENSITIVE_ORDER) { it.title.orEmpty() }.thenBy { it.broadcastId }

    private const val ORIGIN_MANUAL = "MANUAL"
}
