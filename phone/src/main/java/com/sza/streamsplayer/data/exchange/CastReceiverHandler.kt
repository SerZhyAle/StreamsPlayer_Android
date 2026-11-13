package com.sza.streamsplayer.data.exchange

import com.sza.streamsplayer.data.broadcast.BroadcastDescriptorDto
import com.sza.streamsplayer.data.broadcast.BroadcastDescriptorParser
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import timber.log.Timber

/**
 * Handles incoming cast-offer, cast-stop and cast-answer messages on the control stream
 * (contract DEVICE-EXCHANGE section 7.8 and 14.5 item R).
 *
 * Cast receive only occurs between devices of the same account, carries no access secret,
 * and defaults to asking the user (with a 60 s timeout). An immediate play setting is off
 * by default.
 */
class CastReceiverHandler(
    var playImmediately: Boolean = false,
) {
    var onRequestUserConsent: (suspend (broadcast: BroadcastDescriptorDto, fromDeviceId: String) -> Boolean)? = null
    var onStartPlayback: ((broadcast: BroadcastDescriptorDto) -> Unit)? = null
    var onStopPlayback: ((broadcastId: String) -> Unit)? = null

    /** DEVICE-EXCHANGE 27 item DK: the offer's descriptor with the keys of its `sealed` box written in. */
    var openSealed: (suspend (broadcastId: String, descriptorJson: String, sealed: String?) -> String)? = null

    var currentBroadcastId: String? = null
        private set

    suspend fun handleCastOffer(
        offer: DeviceExchangeEnvelope,
        descriptorParser: BroadcastDescriptorParser,
        sendAnswer: (DeviceExchangeEnvelope) -> Unit,
        timeoutMs: Long = 60_000L,
    ) {
        val fromDeviceId = offer.string("fromDeviceId").orEmpty()
        val broadcastObj = offer.obj("broadcast")
        val broadcastId = broadcastObj?.get("broadcastId")?.asString.orEmpty()
        val sealed = broadcastObj?.get("sealed")?.takeIf { it.isJsonObject }?.toString()
        val delivered = broadcastObj?.get("descriptor")?.toString().orEmpty()
        val descriptorJson = openSealed?.invoke(broadcastId, delivered, sealed) ?: delivered

        val parsed = descriptorParser.parseDetailed(descriptorJson)
        val dto = (parsed as? BroadcastDescriptorParser.ParseOutcome.Valid)?.dto

        if (dto == null || !ExchangeReceiverDeclaration.canPlay(dto)) {
            Timber.d("S0020: cast offer unsupported broadcastId=${ExchangeTunnelProxy.hashId(broadcastId)}")
            sendAnswer(DeviceExchangeEnvelope.of(DeviceExchangeEnvelope.TYPE_CAST_ANSWER) {
                addProperty("broadcastId", broadcastId)
                addProperty("accepted", false)
                addProperty(DeviceExchangeEnvelope.KEY_REASON, DeviceExchangeEnvelope.REASON_UNSUPPORTED)
            })
            return
        }

        if (playImmediately) {
            Timber.d("S0020: cast offer accepted immediately broadcastId=${ExchangeTunnelProxy.hashId(broadcastId)}")
            currentBroadcastId = broadcastId
            sendAnswer(DeviceExchangeEnvelope.of(DeviceExchangeEnvelope.TYPE_CAST_ANSWER) {
                addProperty("broadcastId", broadcastId)
                addProperty("accepted", true)
            })
            onStartPlayback?.invoke(dto)
            return
        }

        val prompt = onRequestUserConsent
        if (prompt == null) {
            Timber.d("S0020: no consent prompt available; declining cast broadcastId=${ExchangeTunnelProxy.hashId(broadcastId)}")
            sendAnswer(DeviceExchangeEnvelope.of(DeviceExchangeEnvelope.TYPE_CAST_ANSWER) {
                addProperty("broadcastId", broadcastId)
                addProperty("accepted", false)
                addProperty(DeviceExchangeEnvelope.KEY_REASON, DeviceExchangeEnvelope.REASON_DECLINED)
            })
            return
        }

        val userVerdict = try {
            withTimeout(timeoutMs) {
                prompt(dto, fromDeviceId)
            }
        } catch (_: TimeoutCancellationException) {
            null
        }

        when (userVerdict) {
            true -> {
                Timber.d("S0020: cast offer accepted by user broadcastId=${ExchangeTunnelProxy.hashId(broadcastId)}")
                currentBroadcastId = broadcastId
                sendAnswer(DeviceExchangeEnvelope.of(DeviceExchangeEnvelope.TYPE_CAST_ANSWER) {
                    addProperty("broadcastId", broadcastId)
                    addProperty("accepted", true)
                })
                onStartPlayback?.invoke(dto)
            }
            false -> {
                Timber.d("S0020: cast offer declined by user broadcastId=${ExchangeTunnelProxy.hashId(broadcastId)}")
                sendAnswer(DeviceExchangeEnvelope.of(DeviceExchangeEnvelope.TYPE_CAST_ANSWER) {
                    addProperty("broadcastId", broadcastId)
                    addProperty("accepted", false)
                    addProperty(DeviceExchangeEnvelope.KEY_REASON, DeviceExchangeEnvelope.REASON_DECLINED)
                })
            }
            null -> {
                Timber.d("S0020: cast offer timed out broadcastId=${ExchangeTunnelProxy.hashId(broadcastId)}")
                sendAnswer(DeviceExchangeEnvelope.of(DeviceExchangeEnvelope.TYPE_CAST_ANSWER) {
                    addProperty("broadcastId", broadcastId)
                    addProperty("accepted", false)
                    addProperty(DeviceExchangeEnvelope.KEY_REASON, DeviceExchangeEnvelope.REASON_TIMEOUT)
                })
            }
        }
    }

    fun handleCastStop(stop: DeviceExchangeEnvelope) {
        val broadcastId = stop.string("broadcastId").orEmpty()
        Timber.d("S0020: cast stop received broadcastId=${ExchangeTunnelProxy.hashId(broadcastId)}")
        if (currentBroadcastId == broadcastId) {
            currentBroadcastId = null
            onStopPlayback?.invoke(broadcastId)
        }
    }

    fun handleBroadcastEnded(broadcastId: String) {
        if (currentBroadcastId == broadcastId) {
            Timber.d("S0020: broadcast ended; stopping playback broadcastId=${ExchangeTunnelProxy.hashId(broadcastId)}")
            currentBroadcastId = null
            onStopPlayback?.invoke(broadcastId)
        }
    }
}
