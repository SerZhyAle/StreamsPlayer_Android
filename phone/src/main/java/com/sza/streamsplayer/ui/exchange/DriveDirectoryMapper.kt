package com.sza.streamsplayer.ui.exchange

import com.sza.streamsplayer.data.exchange.ExchangeReceiverDeclaration
import com.sza.streamsplayer.data.exchange.directory.DeviceExchangeDeviceRecord
import com.sza.streamsplayer.data.exchange.drive.DriveAnnouncement
import com.sza.streamsplayer.ui.exchange.model.DeviceBroadcastGroup
import com.sza.streamsplayer.ui.exchange.model.ExchangeBroadcastItem

/** Turns the announcements read from Drive into the groups the broadcast list shows (S0029 scope 3). */
object DriveDirectoryMapper {

    /**
     * One group per announcing device, named by its own device record. A broadcast the exchange server's list
     * already shows ([alreadyListed]) is left out here, so the same broadcast is never listed twice.
     */
    fun groups(
        found: List<DriveAnnouncement>,
        unnamedDevice: String,
        unnamedBroadcast: String,
        alreadyListed: Set<String> = emptySet(),
    ): List<DeviceBroadcastGroup> = found
        .filter { it.broadcast.broadcastId !in alreadyListed }
        .groupBy { it.broadcast.deviceId }
        .map { (deviceId, announcements) ->
            val online = announcements.any { it.deviceOnline }
            DeviceBroadcastGroup(
                deviceId = deviceId,
                deviceName = announcements.firstNotNullOfOrNull { it.deviceName } ?: unnamedDevice,
                presence = if (online) DeviceExchangeDeviceRecord.PRESENCE_ONLINE else DeviceExchangeDeviceRecord.PRESENCE_OFFLINE,
                isOnline = online,
                broadcasts = announcements.map {
                    ExchangeBroadcastItem(
                        broadcastId = it.broadcast.broadcastId,
                        deviceId = deviceId,
                        title = it.broadcast.title?.takeIf(String::isNotBlank) ?: unnamedBroadcast,
                        mode = it.broadcast.mode,
                        descriptorJson = it.broadcast.descriptorJson,
                        isPlayable = ExchangeReceiverDeclaration.modes.any { mode -> mode.equals(it.broadcast.mode, ignoreCase = true) },
                    )
                },
            )
        }
        .sortedBy { it.deviceName.lowercase() }
}
