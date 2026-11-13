package com.sza.streamsplayer.ui.exchange

import com.sza.streamsplayer.data.exchange.directory.DeviceExchangeResourceRecord
import com.sza.streamsplayer.data.exchange.directory.DirectorySnapshot
import com.sza.streamsplayer.ui.exchange.model.LockedResourceItem

/**
 * Splits the directory's resources for the list (contract DEVICE-EXCHANGE item AJ, section 9 point 10): a protected
 * resource is never hidden - it gets its own locked row - while unprotected shared folders, which this product cannot
 * play either, stay behind the one aggregate notice of S0019.
 */
object ExchangeLockedResources {

    fun rows(snapshot: DirectorySnapshot): List<LockedResourceItem> {
        val deviceNames = snapshot.devices.associate { it.deviceId to it.deviceName }
        return snapshot.resources
            .filter { it.isLocked }
            .map { LockedResourceItem(it.resourceId, it.name, deviceNames[it.deviceId]) }
    }

    fun hasUnlockedShares(snapshot: DirectorySnapshot): Boolean =
        snapshot.resources.any { it.kind == DeviceExchangeResourceRecord.KIND_SFTP_SHARE && !it.isLocked }
}
