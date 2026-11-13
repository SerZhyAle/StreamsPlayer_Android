package com.sza.streamsplayer.data.exchange

import com.google.gson.Gson
import com.sza.streamsplayer.data.exchange.session.DeviceExchangeAccountClient
import com.sza.streamsplayer.domain.repository.WearableDataLayerRepository
import com.sza.streamsplayer.service.WearDataLayerPaths
import com.sza.streamsplayer.service.WearSyncEvents
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import java.util.UUID
import javax.inject.Inject

data class WatchExchangeLeaveRequest(@com.google.gson.annotations.SerializedName("requestId") val requestId: String, @com.google.gson.annotations.SerializedName("deviceId") val deviceId: String)
data class WatchExchangeLeaveAck(@com.google.gson.annotations.SerializedName("requestId") val requestId: String, @com.google.gson.annotations.SerializedName("deviceId") val deviceId: String, @com.google.gson.annotations.SerializedName("removed") val removed: Boolean)
data class WatchExchangeLeaveResult(val revokedFromPhone: Int = 0, val unreachable: Int = 0)

/** Removes the watch before the phone's token goes away, with a bounded hand-over and server fallback. */
class WatchExchangeLeaveCoordinator @Inject constructor(
    private val watches: WatchExchangeDeviceStore,
    private val dataLayer: WearableDataLayerRepository,
    private val client: DeviceExchangeAccountClient,
    private val gson: Gson,
) {
    suspend fun remove(entry: DeviceExchangeAccountEntry): WatchExchangeLeaveResult = withContext(Dispatchers.IO) {
        var revoked = 0
        var unavailable = 0
        val connected = dataLayer.getConnectedNodes().map { it.id }.toSet()
        for ((nodeId, deviceId) in watches.devices(entry)) {
            val request = WatchExchangeLeaveRequest(UUID.randomUUID().toString(), deviceId)
            val removedOnWatch = if (nodeId !in connected) false else withTimeoutOrNull(5_000) {
                WearSyncEvents.exchangeLeaveAckFlow.onSubscription {
                    try { dataLayer.sendMessage(nodeId, WearDataLayerPaths.EXCHANGE_LEAVE, gson.toJson(request).toByteArray()) }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { emit(WatchExchangeLeaveAck(request.requestId, deviceId, false)) }
                }.first { it.requestId == request.requestId && it.deviceId == deviceId }.removed
            } ?: false
            if (!removedOnWatch) {
                try { client.revoke(entry, deviceId); revoked++ }
                catch (_: IOException) { unavailable++ }
            }
        }
        watches.forget(entry)
        WatchExchangeLeaveResult(revoked, unavailable)
    }
}
