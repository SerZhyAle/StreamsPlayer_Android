package com.sza.streamsplayer.data.exchange.session

import com.sza.streamsplayer.data.exchange.DeviceExchangeEnvelope
import com.sza.streamsplayer.data.exchange.DeviceExchangeFrameCodec
import com.sza.streamsplayer.data.exchange.ExchangeConnection
import java.io.Closeable

/**
 * An authenticated stream: `hello` or `enroll` was answered with [welcome], so the larger frame limit of 7.1
 * (262,144 bytes) holds in both directions. Writes are synchronized because the control loop, keepalive
 * and directory requests may share the stream.
 */
class DeviceExchangeSession(
    val connection: ExchangeConnection,
    val welcome: DeviceExchangeWelcome,
) : Closeable {

    @Synchronized
    fun send(envelope: DeviceExchangeEnvelope) =
        DeviceExchangeFrameCodec.write(
            connection.output,
            envelope,
            DeviceExchangeFrameCodec.MAX_FRAME_BODY_AUTHENTICATED,
        )

    /** The next envelope, or null at a clean end of the stream. */
    fun receive(): DeviceExchangeEnvelope? =
        DeviceExchangeFrameCodec.read(
            connection.input,
            DeviceExchangeFrameCodec.MAX_FRAME_BODY_AUTHENTICATED,
        )

    override fun close() = connection.close()
}
