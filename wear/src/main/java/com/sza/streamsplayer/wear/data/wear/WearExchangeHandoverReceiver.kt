package com.sza.streamsplayer.wear.data.wear

import android.content.Context
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.wearable.Wearable
import com.google.gson.Gson
import com.sza.streamsplayer.wear.data.exchange.WearExchangeAccountRepository
import com.sza.streamsplayer.data.exchange.session.DeviceExchangeE2eKeyStore
import com.sza.streamsplayer.data.exchange.session.DeviceExchangeEnrollResult
import com.sza.streamsplayer.wear.data.exchange.WearExchangeJoin
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import java.time.Instant
import java.time.format.DateTimeParseException
import javax.inject.Inject

/**
 * S4158: takes one exchange entry the phone handed over, enrolls this watch into its account with the pairing code
 * and answers. The answer is sent for every outcome, refusals included: the phone must tell "the code expired"
 * from "the watch never got it", because those ask the owner for different things.
 *
 * Nothing logged here names the code, the token, the login or the address (contract section 7.9).
 */
class WearExchangeHandoverReceiver @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gson: Gson,
    private val accounts: WearExchangeAccountRepository,
    private val e2eKeys: DeviceExchangeE2eKeyStore,
) {

    suspend fun handle(nodeId: String, data: ByteArray) {
        val ack = when (val parsed = WearExchangeHandoverCodec.parse(data, gson)) {
            WearExchangeHandoverParseResult.Malformed -> refusal(requestId = "", WearExchangeHandoverReasons.MALFORMED)
            is WearExchangeHandoverParseResult.UnsupportedVersion -> WearExchangeHandoverValidation.unsupportedVersionRefusal(parsed)
            is WearExchangeHandoverParseResult.Parsed -> take(parsed.payload)
        }
        answer(nodeId, ack)
    }

    private suspend fun take(payload: WearExchangeHandoverPayload): WearExchangeHandoverAck =
        if (WearExchangeHandoverValidation.isExpired(payload.expiresAt, Instant.now())) {
            refusal(payload.requestId, WearExchangeHandoverReasons.CODE_EXPIRED)
        } else {
            val join = WearExchangeJoin(
                host = payload.host,
                port = payload.port,
                login = payload.login,
                pinnedCertificate = payload.pinnedCertificate,
                pairingCode = payload.pairingCode,
                pinnedKey = payload.pinnedKey?.takeIf { payload.pinnedCertificate != null },
            )
            try { acknowledge(payload.requestId, accounts.enroll(join)) }
            catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (_: Exception) { refusal(payload.requestId, WearExchangeHandoverReasons.TOKEN_NOT_STORED) }
        }

    private suspend fun acknowledge(requestId: String, result: DeviceExchangeEnrollResult): WearExchangeHandoverAck =
        when (result) {
            is DeviceExchangeEnrollResult.Enrolled -> {
                Timber.i("Exchange hand-over: this watch enrolled")
                val deviceId = accounts.deviceId()
                // Item DG: the phone trusts this key over the directory, where the server could have put another.
                val kem = deviceId?.let { e2eKeys.existing(it)?.kem }
                WearExchangeHandoverAck(requestId, enrolled = true, deviceId = deviceId, e2eKem = kem)
            }
            is DeviceExchangeEnrollResult.Refused -> refusal(requestId, result.reason)
            is DeviceExchangeEnrollResult.CertificateUntrusted ->
                refusal(requestId, WearExchangeHandoverReasons.CERTIFICATE_UNTRUSTED)
            is DeviceExchangeEnrollResult.CertificateChanged ->
                refusal(requestId, WearExchangeHandoverReasons.CERTIFICATE_CHANGED)
            is DeviceExchangeEnrollResult.Unreachable -> {
                Timber.i("Watch exchange enrollment could not reach the server")
                refusal(requestId, WearExchangeHandoverReasons.UNREACHABLE)
            }
        }

    private suspend fun answer(nodeId: String, ack: WearExchangeHandoverAck) {
        if (nodeId.isBlank()) return
        try {
            Wearable.getMessageClient(context)
                .sendMessage(
                    nodeId,
                    WearDataLayerPaths.EXCHANGE_HANDOVER_ACK,
                    WearExchangeHandoverCodec.serializeAck(ack, gson),
                )
                .await()
        } catch (e: ApiException) {
            // The phone then waits out its timeout and reports that the watch did not answer.
            Timber.e(e, "Exchange hand-over: the answer could not be sent")
        }
    }

    private fun refusal(requestId: String, reason: String) =
        WearExchangeHandoverAck(requestId, enrolled = false, reason = reason)
}
