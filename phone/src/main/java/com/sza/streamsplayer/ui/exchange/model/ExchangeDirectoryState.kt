package com.sza.streamsplayer.ui.exchange.model

/**
 * State of the exchange directory view (S0019 Scope 6).
 */
data class ExchangeDirectoryState(
    val isLoading: Boolean = false,
    val devices: List<DeviceBroadcastGroup> = emptyList(),
    val hasSftpShares: Boolean = false,
    val lockedResources: List<LockedResourceItem> = emptyList(),
    val errorText: String? = null,
    val errorRes: Int? = null,
) {
    val totalPlayableBroadcasts: Int
        get() = devices.sumOf { dev -> dev.broadcasts.count { it.isPlayable } }

    val isEmpty: Boolean
        get() = devices.isEmpty() || totalPlayableBroadcasts == 0
}

/**
 * Live broadcasts grouped by device (S0019 Scope 6).
 */
data class DeviceBroadcastGroup(
    val deviceId: String,
    val deviceName: String,
    val presence: String,
    val isOnline: Boolean,
    val broadcasts: List<ExchangeBroadcastItem>,
)

/**
 * A resource behind a PIN, listed locked and never opened (contract DEVICE-EXCHANGE item AJ, section 9 point 10):
 * this product declares no `pin-v1`, so the row names the resource and its device and asks for nothing.
 */
data class LockedResourceItem(
    val resourceId: String,
    val name: String,
    val deviceName: String?,
)

/**
 * One live broadcast listed on the exchange server (S0019 Scope 6).
 */
data class ExchangeBroadcastItem(
    val broadcastId: String,
    val deviceId: String,
    val title: String,
    val mode: String,
    val descriptorJson: String,
    val isPlayable: Boolean,
    val isKept: Boolean = false,
    /** Why the broadcast's box did not open here (DEVICE-EXCHANGE 27 item DR); the row is then not playable. */
    val sealNotice: com.sza.streamsplayer.domain.model.ExchangeMessage? = null,
)
