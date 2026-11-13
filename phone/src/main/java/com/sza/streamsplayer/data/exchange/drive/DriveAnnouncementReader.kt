package com.sza.streamsplayer.data.exchange.drive

import com.sza.streamsplayer.data.exchange.directory.DeviceExchangeBroadcastRecord
import com.sza.streamsplayer.data.exchange.directory.DeviceExchangeDeviceRecord
import com.sza.streamsplayer.data.exchange.directory.DeviceExchangeRecordCodec
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/** One broadcast another device of the person's Google account announces in the app folder. */
data class DriveAnnouncement(
    val broadcast: DeviceExchangeBroadcastRecord,
    /** The announcing device's name from its own device record; null when it wrote none. */
    val deviceName: String?,
    val deviceOnline: Boolean,
)

/**
 * Reads the broadcast announcements of the signed-in account (S0029 scope 3; DEVICE-EXCHANGE 8.1, items C, CL).
 *
 * A record is believed only inside its own lifetime, judged on Drive's clock: the file's `modifiedTime`
 * against the `Date` of the listing (item C). A record past it is an ended broadcast and is not returned -
 * never as live, never kept as history (6.3). This phone's own records are hidden (9.1), and a reader never
 * deletes a file it did not write.
 */
@Singleton
class DriveAnnouncementReader @Inject constructor(
    private val api: DriveFilesApi,
    private val channel: DriveChannelStore,
) {

    suspend fun read(): List<DriveAnnouncement> {
        val listing = api.list(DriveAppFolder.RECORD_PREFIX)
        val own = channel.deviceId
        val broadcastFiles = listing.files
            .filter { it.name.startsWith(DriveAppFolder.BROADCAST_PREFIX) && withinAnyLifetime(it, listing.driveNowMs) }
            .sortedByDescending { it.modifiedTimeMs }
            .take(DriveAppFolder.MAX_FILES_READ_PER_KIND)

        val live = broadcastFiles.mapNotNull { file ->
            val record = decode(file) as? DeviceExchangeBroadcastRecord ?: return@mapNotNull null
            val named = file.name == DeviceExchangeRecordCodec.fileName(DeviceExchangeRecordCodec.TYPE_BROADCAST, record.broadcastId)
            val ttl = DriveAppFolder.believedTtlSeconds(record.ttlSeconds, DriveAppFolder.BROADCAST_TTL_SECONDS)
            when {
                !named -> null
                record.deviceId == own -> null
                isExpired(file, listing.driveNowMs, ttl) -> null
                else -> record
            }
        }

        val devices = live.map { it.deviceId }.distinct().associateWith { deviceId ->
            val name = DeviceExchangeRecordCodec.fileName(DeviceExchangeRecordCodec.TYPE_DEVICE, deviceId)
            listing.files.firstOrNull { it.name == name }?.let { file ->
                val record = decode(file) as? DeviceExchangeDeviceRecord
                record?.takeIf { it.deviceId == deviceId }?.let { it to file }
            }
        }

        val announcements = live.map { broadcast ->
            val device = devices[broadcast.deviceId]
            val online = device?.let { (record, file) ->
                val ttl = DriveAppFolder.believedTtlSeconds(record.ttlSeconds, DriveAppFolder.DEVICE_TTL_SECONDS)
                record.isOnline && !isExpired(file, listing.driveNowMs, ttl)
            } ?: true
            DriveAnnouncement(broadcast, device?.first?.deviceName?.takeIf { it.isNotBlank() }, online)
        }
        Timber.d("S0029: ${announcements.size} announcement(s) of other devices listed")
        return announcements
    }

    private suspend fun decode(file: DriveFileInfo) =
        api.read(file.id)?.let(DeviceExchangeRecordCodec::decode)

    /** A file older than the longest lifetime anyone is believed on cannot be live whatever it claims. */
    private fun withinAnyLifetime(file: DriveFileInfo, driveNowMs: Long): Boolean =
        driveNowMs - file.modifiedTimeMs < DriveAppFolder.MAX_BELIEVED_TTL_SECONDS * MS

    private fun isExpired(file: DriveFileInfo, driveNowMs: Long, ttlSeconds: Long): Boolean =
        driveNowMs - file.modifiedTimeMs >= ttlSeconds * MS

    private companion object {
        const val MS = 1_000L
    }
}
