package com.sza.streamsplayer.data.exchange

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.sza.streamsplayer.data.broadcast.BroadcastDescriptorDto

/**
 * Receiver declaration of contract DEVICE-EXCHANGE section 6.1 and 14.4 item E.
 *
 * Declares the modes and transports StreamsPlayer Android really implements:
 * Modes: AUDIO_ONLY, VIDEO_AUDIO, VIDEO_ONLY
 * Transports: HTTP, RTSP, RELAY, TUNNEL (P2P is reserved and NOT built).
 * All mode/transport combinations are supported; omit plays instead of exceeding the wire's 8-pair bound.
 */
object ExchangeReceiverDeclaration {

    val modes: List<String> = listOf("AUDIO_ONLY", "VIDEO_AUDIO", "VIDEO_ONLY")
    val transports: List<String> = listOf("HTTP", "RTSP", "RELAY", "TUNNEL")

    data class PlayPair(val mode: String, val transport: String)

    val plays: List<PlayPair> = modes.flatMap { m ->
        transports.map { t -> PlayPair(mode = m, transport = t) }
    }

    fun canPlay(mode: String, transport: String): Boolean =
        plays.any { it.mode.equals(mode, ignoreCase = true) && it.transport.equals(transport, ignoreCase = true) }

    fun canPlay(descriptor: BroadcastDescriptorDto): Boolean {
        val endpoints = descriptor.getEffectiveEndpoints()
        return endpoints.any { canPlay(it.mode, it.transport) }
    }

    fun toJson(): JsonObject {
        val json = JsonObject()
        val modesArray = JsonArray().apply { modes.forEach { add(it) } }
        val transportsArray = JsonArray().apply { transports.forEach { add(it) } }
        json.add("modes", modesArray)
        json.add("transports", transportsArray)
        return json
    }
}
