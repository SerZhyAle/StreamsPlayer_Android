package com.sza.streamsplayer.data.exchange.directory

/** Port of the donor's revision reducer (DEVICE-EXCHANGE 7.4). */
class DeviceExchangeDirectoryState private constructor(val directory: DirectorySnapshot) {
    sealed interface Outcome {
        data class Applied(val state: DeviceExchangeDirectoryState) : Outcome
        data object Stale : Outcome
        data object Gap : Outcome
    }

    val revision: Int get() = directory.revision

    fun apply(changed: DirectoryChanged): Outcome = when {
        changed.revision <= revision -> Outcome.Stale
        changed.revision != revision + 1 -> Outcome.Gap
        else -> Outcome.Applied(DeviceExchangeDirectoryState(merged(changed)))
    }

    // A removed device takes its resources and broadcasts with it, even if not named separately.
    private fun merged(changed: DirectoryChanged): DirectorySnapshot {
        val goneDevices = changed.removals.deviceIds
        return DirectorySnapshot(
            revision = changed.revision,
            devices = upsert(directory.devices, changed.upserts.devices, DeviceExchangeDeviceRecord::deviceId)
                .filterNot { it.deviceId in goneDevices },
            resources = upsert(directory.resources, changed.upserts.resources, DeviceExchangeResourceRecord::resourceId)
                .filterNot { it.resourceId in changed.removals.resourceIds || it.deviceId in goneDevices },
            broadcasts = upsert(directory.broadcasts, changed.upserts.broadcasts, DeviceExchangeBroadcastRecord::broadcastId)
                .filterNot { it.broadcastId in changed.removals.broadcastIds || it.deviceId in goneDevices },
        )
    }

    private fun <T> upsert(held: List<T>, upserts: List<T>, id: (T) -> String): List<T> {
        val replaced = upserts.associateBy(id)
        val kept = held.map { replaced[id(it)] ?: it }
        val heldIds = held.mapTo(HashSet(), id)
        return kept + upserts.filterNot { id(it) in heldIds }
    }

    companion object {
        fun of(directory: DirectorySnapshot) = DeviceExchangeDirectoryState(directory)
    }
}
