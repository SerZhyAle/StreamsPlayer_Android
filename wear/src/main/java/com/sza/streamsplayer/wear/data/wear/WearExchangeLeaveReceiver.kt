package com.sza.streamsplayer.wear.data.wear

import android.content.Context
import com.google.android.gms.wearable.Wearable
import com.google.gson.Gson
import com.sza.streamsplayer.wear.data.exchange.WearExchangeAccountRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class WearExchangeLeaveReceiver @Inject constructor(
    @ApplicationContext private val context: Context,
    private val accounts: WearExchangeAccountRepository,
    private val gson: Gson,
) {
    private data class Request(@com.google.gson.annotations.SerializedName("requestId") val requestId: String, @com.google.gson.annotations.SerializedName("deviceId") val deviceId: String)
    private data class Ack(@com.google.gson.annotations.SerializedName("requestId") val requestId: String, @com.google.gson.annotations.SerializedName("deviceId") val deviceId: String, @com.google.gson.annotations.SerializedName("removed") val removed: Boolean)

    suspend fun handle(nodeId: String, bytes: ByteArray) {
        val request = runCatching { gson.fromJson(bytes.decodeToString(), Request::class.java) }.getOrNull() ?: return
        val required: List<String?> = listOf(request.requestId, request.deviceId)
        if (required.any { it.isNullOrBlank() }) return
        val result = accounts.removeWithResult(request.deviceId)
        Wearable.getMessageClient(context).sendMessage(nodeId, WearDataLayerPaths.EXCHANGE_LEAVE_ACK,
            gson.toJson(Ack(request.requestId, request.deviceId, result.forgotten && result.revoked)).toByteArray()).await()
    }
}
