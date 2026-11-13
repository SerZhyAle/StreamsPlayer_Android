package com.sza.streamsplayer.data.exchange.publish

import com.sza.streamsplayer.data.broadcast.BroadcastEndpointDto
import com.sza.streamsplayer.data.exchange.DeviceExchangeEnvelope
import com.sza.streamsplayer.data.exchange.directory.DeviceExchangeReceiverDeclaration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import com.sza.streamsplayer.data.exchange.ExchangeLog as Timber
import java.util.concurrent.ConcurrentHashMap

/**
 * The caster half of cast (DEVICE-EXCHANGE 7.8, 14.5 item R). `cast {broadcastId, receiverDeviceId}`
 * goes out on the control stream and answers nothing; the server forwards `cast-offer` to the
 * receiver and brings back `cast-result`, which this class maps and hands to [onResult]. A receiver
 * that says nothing within 60 s is answered locally with `timeout` (the working default of item R).
 * A cast never carries `access`, and the broadcast id in a log is the id's hash (7.9).
 */
class CastRequestSender(
    private val send: suspend (DeviceExchangeEnvelope) -> Boolean,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** One outstanding cast per broadcast; a second cast to the same broadcast replaces it. */
    private val pending = ConcurrentHashMap<String, Channel<DeviceExchangeEnvelope>>()

    var onResult: ((CastResult) -> Unit)? = null

    fun cast(broadcastId: String, receiverDeviceId: String) {
        val answers = Channel<DeviceExchangeEnvelope>(capacity = 1)
        val previous = pending.put(broadcastId, answers)
        previous?.close()
        val broadcastHash = BroadcastTunnelAcceptor.hashId(broadcastId)
        Timber.d("S0021: cast offered broadcastId=$broadcastHash receiverDeviceId=$receiverDeviceId")
        scope.launch {
            try {
                send(
                    DeviceExchangeEnvelope.of(DeviceExchangeEnvelope.TYPE_CAST) {
                        addProperty(KEY_BROADCAST_ID, broadcastId)
                        addProperty(KEY_RECEIVER_DEVICE_ID, receiverDeviceId)
                    }
                )
                val answer = withTimeoutOrNull(RESULT_TIMEOUT_MS) { answers.receive() }
                val result = answer?.let(::parseResult)
                    ?: CastResult(broadcastId, receiverDeviceId, accepted = false, reason = REASON_TIMEOUT)
                pending.remove(broadcastId, answers)
                Timber.d("S0021: cast result broadcastId=$broadcastHash accepted=${result.accepted} reason=${result.reason}")
                onResult?.invoke(result)
            } catch (_: Exception) {
                pending.remove(broadcastId, answers)
            }
        }
    }

    /** `cast-stop`, forwarded as is by the server (7.8); the pending answer, if any, is retired. */
    fun stop(broadcastId: String, receiverDeviceId: String) {
        pending.remove(broadcastId)?.close()
        scope.launch {
            send(
                DeviceExchangeEnvelope.of(DeviceExchangeEnvelope.TYPE_CAST_STOP) {
                    addProperty(KEY_BROADCAST_ID, broadcastId)
                    addProperty(KEY_RECEIVER_DEVICE_ID, receiverDeviceId)
                }
            )
        }
    }

    /** Entry point from the control stream reader; drops what answers no cast. */
    fun onCastResult(envelope: DeviceExchangeEnvelope) {
        val parsed = parseResult(envelope) ?: return
        pending[parsed.broadcastId]?.trySend(envelope)
    }

    fun close() {
        pending.values.forEach { it.close() }
        pending.clear()
    }

    data class CastResult(
        val broadcastId: String,
        val receiverDeviceId: String,
        val accepted: Boolean,
        val reason: String?,
    )

    companion object {
        private const val KEY_BROADCAST_ID = "broadcastId"
        private const val KEY_RECEIVER_DEVICE_ID = "receiverDeviceId"

        /** The four reasons of 14.5 item R; an unknown reason is shown as a generic refusal. */
        const val REASON_UNAVAILABLE = "unavailable"
        const val REASON_DECLINED = "declined"
        const val REASON_TIMEOUT = "timeout"
        const val REASON_UNSUPPORTED = "unsupported"

        val KNOWN_REASONS = setOf(REASON_UNAVAILABLE, REASON_DECLINED, REASON_TIMEOUT, REASON_UNSUPPORTED)

        /** The no-answer window of item R: 60 s, a working default. */
        const val RESULT_TIMEOUT_MS = 60_000L

        fun parseResult(envelope: DeviceExchangeEnvelope): CastResult? {
            val broadcastId = envelope.string(KEY_BROADCAST_ID)?.takeIf(String::isNotBlank) ?: return null
            val receiver = envelope.string(KEY_RECEIVER_DEVICE_ID)?.takeIf(String::isNotBlank) ?: return null
            val accepted = envelope.boolean("accepted") ?: return null
            return CastResult(broadcastId, receiver, accepted, envelope.string(DeviceExchangeEnvelope.KEY_REASON))
        }
    }
}

/**
 * Whether a receiver may be offered a cast of a broadcast (DEVICE-EXCHANGE 6.1, 14.5 item R): the
 * receiver's `plays` pairs decide when present, else the summary `modes` and `transports` do.
 */
object CastEligibility {

    fun covers(
        declaration: DeviceExchangeReceiverDeclaration?,
        endpoints: List<BroadcastEndpointDto>,
    ): Boolean {
        if (declaration == null) return false
        val offered = endpoints.map { Pair(it.mode.uppercase(), it.transport.uppercase()) }
        declaration.plays?.let { plays ->
            return offered.any { pair ->
                plays.any { it.mode.uppercase() == pair.first && it.transport.uppercase() == pair.second }
            }
        }
        val modes = declaration.modes.map(String::uppercase)
        val transports = declaration.transports.map(String::uppercase)
        return offered.any { it.first in modes && it.second in transports }
    }
}
