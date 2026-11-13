package com.sza.streamsplayer.data.exchange.directory

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.sza.streamsplayer.data.exchange.DeviceExchangeEnvelope
import com.sza.streamsplayer.data.exchange.session.DeviceExchangeAccountClient
import com.sza.streamsplayer.data.exchange.session.DeviceExchangeRefusedException
import com.sza.streamsplayer.data.exchange.session.DeviceExchangeSession
import com.sza.streamsplayer.data.exchange.ExchangeLog as Timber

/** Snapshot of the account's directory (contract DEVICE-EXCHANGE 7.4). */
data class DirectorySnapshot(
    val revision: Int,
    val devices: List<DeviceExchangeDeviceRecord>,
    val resources: List<DeviceExchangeResourceRecord>,
    val broadcasts: List<DeviceExchangeBroadcastRecord>,
)

/** Removals in a `changed` push message. */
data class DirectoryRemovals(
    val deviceIds: List<String>,
    val resourceIds: List<String>,
    val broadcastIds: List<String>,
)

/** Pushed change delta with revision and gap-tracking info. */
data class DirectoryChanged(
    val revision: Int,
    val upserts: DirectorySnapshot,
    val removals: DirectoryRemovals,
)

/**
 * Client for directory operations over an authenticated exchange session (DEVICE-EXCHANGE 7.4).
 * Handles `list`, `subscribe`, `changed` push notifications, revision gap detection, and `bye`.
 *
 * Records are read leniently:
 * - unknown members ignored
 * - unknown kind skipped
 * - record above 16 KiB refused/skipped
 * - resource without access listed with null descriptor (section 15 item X)
 */
class DeviceExchangeDirectoryClient(
    private val session: DeviceExchangeSession? = null,
) {
    @Volatile
    var lastRevision: Int = 0
        private set

    /** Sends `list` and reads back the `directory` snapshot. */
    fun list(): DirectorySnapshot {
        Timber.d("S0016: sending list request")
        checkNotNull(session).send(DeviceExchangeEnvelope.of(DeviceExchangeEnvelope.TYPE_LIST))
        val response = checkNotNull(session).receive() ?: throw IllegalStateException("connection closed before directory received")
        if (response.type == DeviceExchangeEnvelope.TYPE_REFUSED) {
            throw DeviceExchangeRefusedException(
                response.string(DeviceExchangeEnvelope.KEY_REASON) ?: DeviceExchangeAccountClient.REASON_UNKNOWN,
            )
        }
        if (response.type != DeviceExchangeEnvelope.TYPE_DIRECTORY) {
            throw IllegalStateException("expected directory envelope, got: ${response.type}")
        }
        val snapshot = parseSnapshot(response)
        lastRevision = snapshot.revision
        Timber.d("S0016: directory received revision=${snapshot.revision} devices=${snapshot.devices.size} resources=${snapshot.resources.size} broadcasts=${snapshot.broadcasts.size}")
        return snapshot
    }

    /** Sends `subscribe` to receive `changed` pushes. */
    fun subscribe() {
        Timber.d("S0016: subscribing to directory changes")
        checkNotNull(session).send(DeviceExchangeEnvelope.of(DeviceExchangeEnvelope.TYPE_SUBSCRIBE))
    }

    /** Sends `bye` before closing. */
    fun bye() {
        Timber.d("S0016: sending bye")
        checkNotNull(session).send(DeviceExchangeEnvelope.of(DeviceExchangeEnvelope.TYPE_BYE))
    }

    /**
     * Parses and applies a `changed` envelope.
     * If a gap in revision is observed (`newRevision > lastRevision + 1`), invokes [onRevisionGap]
     * so caller can trigger a full [list] resync.
     */
    fun handleChanged(
        envelope: DeviceExchangeEnvelope,
        onRevisionGap: () -> Unit = {},
    ): DirectoryChanged {
        val revision = envelope.int("revision") ?: 0
        if (lastRevision > 0 && revision > lastRevision + 1) {
            Timber.d("S0016: directory revision gap detected expected=${lastRevision + 1} got=$revision; resyncing")
            onRevisionGap()
        }
        lastRevision = revision

        val upsertsObj = envelope.obj("upserts") ?: JsonObject()
        val removalsObj = envelope.obj("removals") ?: JsonObject()

        val upserts = parseSnapshotFromObject(revision, upsertsObj)
        val removals = DirectoryRemovals(
            deviceIds = stringList(removalsObj, "deviceIds"),
            resourceIds = stringList(removalsObj, "resourceIds"),
            broadcastIds = stringList(removalsObj, "broadcastIds"),
        )
        return DirectoryChanged(revision, upserts, removals)
    }

    fun parseSnapshot(envelope: DeviceExchangeEnvelope): DirectorySnapshot {
        val revision = envelope.int("revision") ?: 0
        return parseSnapshotFromObject(revision, envelope.members)
    }

    private fun parseSnapshotFromObject(revision: Int, obj: JsonObject): DirectorySnapshot {
        val devices = (obj.get("devices") as? JsonArray)?.mapNotNull { el ->
            (el as? JsonObject)?.let { devObj ->
                if (devObj.toString().toByteArray(Charsets.UTF_8).size > DeviceExchangeRecordCodec.MAX_RECORD_BYTES) {
                    null
                } else {
                    DeviceExchangeRecordCodec.decodeDevice(devObj, System.currentTimeMillis(), 1800)
                }
            }
        }.orEmpty()

        val resources = (obj.get("resources") as? JsonArray)?.mapNotNull { el ->
            (el as? JsonObject)?.let { resObj ->
                if (resObj.toString().toByteArray(Charsets.UTF_8).size > DeviceExchangeRecordCodec.MAX_RECORD_BYTES) {
                    null
                } else {
                    DeviceExchangeRecordCodec.decodeResource(resObj, System.currentTimeMillis(), 1800)
                }
            }
        }.orEmpty()

        val broadcasts = (obj.get("broadcasts") as? JsonArray)?.mapNotNull { el ->
            (el as? JsonObject)?.let { bcastObj ->
                if (bcastObj.toString().toByteArray(Charsets.UTF_8).size > DeviceExchangeRecordCodec.MAX_RECORD_BYTES) {
                    null
                } else {
                    DeviceExchangeRecordCodec.decodeBroadcast(bcastObj, System.currentTimeMillis(), 120)
                }
            }
        }.orEmpty()

        return DirectorySnapshot(revision, devices, resources, broadcasts)
    }

    private fun stringList(obj: JsonObject, key: String): List<String> =
        (obj.get(key) as? JsonArray)?.mapNotNull { it.takeIf { p -> p.isJsonPrimitive && p.asJsonPrimitive.isString }?.asString }.orEmpty()
}
