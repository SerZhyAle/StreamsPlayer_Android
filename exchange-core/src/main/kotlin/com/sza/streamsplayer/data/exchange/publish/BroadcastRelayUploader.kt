package com.sza.streamsplayer.data.exchange.publish

import com.sza.streamsplayer.data.exchange.DeviceExchangeAccountEntry
import com.sza.streamsplayer.data.exchange.DeviceExchangeEnvelope
import com.sza.streamsplayer.data.exchange.session.DeviceExchangeAccountClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.sza.streamsplayer.data.exchange.ExchangeLog as Timber
import java.io.Closeable
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Uploads one broadcast to the fan-out relay (DEVICE-EXCHANGE 7.6): a data stream, `hello`,
 * `upload {broadcastId, contentType}`, then chunks.
 *
 * `AUDIO_ONLY` rides ADTS AAC-LC as `audio/aac`, every chunk whole frames and joinable - one encoder
 * frame per chunk, the smallest joinable unit the LAN server already serves ([writeFrame]). The video
 * modes ride MPEG-TS as `video/mp2t`: the [header] chunk (PAT and PMT) goes out first on every
 * upload stream, then the chunks [TsRelayChunker] cuts ([writeChunk]), the first of them a joinable
 * one.
 *
 * Reconnects are bounded and backed off: a spent bound calls [onDropped] and stops, the LAN broadcast
 * continues - never a silent re-try loop (S0021 scope 8). A fresh upload for a live broadcast replaces
 * the first without dropping listeners (14.5 item N): it starts with the header chunk again and
 * resumes at the next joinable chunk, because a chunk from the middle of a group of pictures would
 * only decode as garbage. The same rule covers a full queue: the stalled relay loses its oldest chunk,
 * and nothing is sent until a joinable chunk makes the stream decodable again.
 *
 * The broadcast id is the capability of the secret-link model and is never logged (7.9).
 */
class BroadcastRelayUploader(
    private val accountClient: DeviceExchangeAccountClient,
    private val entry: DeviceExchangeAccountEntry,
    private val broadcastId: String,
    private val contentType: String,
    private val header: ByteArray? = null,
    queueCapacity: Int = FRAME_QUEUE_FRAMES,
    private val onAccepted: () -> Unit = {},
    private val sourceUrl: String? = null,
    /**
     * Item DL: the sealer of a new epoch, drawn for every upload stream; null for a clear upload. A sealed upload
     * whose epoch cannot be drawn sends nothing.
     */
    private val sealers: (() -> ExchangeRelaySealer?)? = null,
    private val onDropped: (cause: String) -> Unit,
) : Closeable {

    private class Item(val flag: Int, val bytes: ByteArray)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Bounded like a listener sink: a stalled relay loses its oldest chunk, never the newest. */
    private val items = Channel<Item>(capacity = queueCapacity)

    private val closed = AtomicBoolean(false)
    private val session = java.util.concurrent.atomic.AtomicReference<com.sza.streamsplayer.data.exchange.session.DeviceExchangeSession?>()
    private val source = java.util.concurrent.atomic.AtomicReference<java.net.HttpURLConnection?>()

    /** True until a joinable chunk has gone out on this stream: nothing else may start it. */
    @Volatile
    private var needJoinable = true

    /** Queues one whole ADTS frame for the upload; the encoder allocates a fresh array per frame. */
    fun writeFrame(frame: ByteArray) {
        offer(Item(RelayChunk.FLAG_JOINABLE, frame))
    }

    /** Queues one chunk cut by [TsRelayChunker]; [flag] is joinable or ordinary. */
    fun writeChunk(flag: Int, payload: ByteArray) {
        if (flag == RelayChunk.FLAG_JOINABLE || flag == RelayChunk.FLAG_ORDINARY) {
            offer(Item(flag, payload))
        }
    }

    private fun offer(item: Item) {
        if (closed.get()) return
        if (!items.trySend(item).isSuccess) {
            items.tryReceive()
            needJoinable = true
            items.trySend(item)
        }
    }

    fun start() {
        scope.launch { uploadLoop() }
    }

    private suspend fun uploadLoop() {
        var failures = 0
        while (failures < MAX_FAILURES) {
            if (closed.get()) return
            try {
                uploadOnce()
                return
            } catch (e: CancellationException) {
                throw e
            } catch (e: ExchangeRelaySealer.EpochReplaced) {
                // A device left or a link changed: the replacement upload opens the next epoch at once (item DL).
                Timber.d("Relay upload gives way to a new epoch")
            } catch (e: Exception) {
                if (closed.get()) return
                failures += 1
                if (failures >= MAX_FAILURES) {
                    Timber.d(e, "S0021: relay upload dropped after $failures failures")
                    onDropped(e.message ?: "relay upload failed")
                    return
                }
                Timber.d(e, "S0021: relay upload failed, attempt $failures of $MAX_FAILURES")
                delay(RETRY_BACKOFF_MS[failures - 1])
            }
        }
    }

    /**
     * One upload stream, kept open until [close] or the wire fails. The zero-length chunk of 7.6
     * goes out before the stream closes whenever the failure is ours and not the wire's.
     */
    private suspend fun uploadOnce() {
        accountClient.openAuthenticated(entry).use { session ->
            this.session.set(session)
            if (closed.get()) { session.close(); return }
            session.connection.socket.soTimeout = 15_000
            session.send(
                DeviceExchangeEnvelope.of(DeviceExchangeEnvelope.TYPE_UPLOAD) {
                    addProperty(KEY_BROADCAST_ID, broadcastId)
                    addProperty(KEY_CONTENT_TYPE, contentType)
                }
            )
            val answer = session.receive()
                ?: throw IOException("relay closed the stream before upload-accepted")
            if (answer.type != DeviceExchangeEnvelope.TYPE_UPLOAD_ACCEPTED) {
                val reason = answer.string(DeviceExchangeEnvelope.KEY_REASON) ?: "no upload-accepted"
                throw IOException("relay refused the upload: $reason")
            }
            Timber.d("S0021: relay upload accepted contentType=$contentType")
            val sealer = sealers?.let { draw -> draw() ?: throw IOException("no relay epoch for a sealed upload") }
            val writer = RelayChunkWriter(session.connection.output)
            if (sealer != null) {
                writer.writeHeader(sealer.headerChunk(header))
            } else {
                header?.let(writer::writeHeader)
            }
            needJoinable = true
            if (sourceUrl != null) {
                forwardSource(sourceUrl, writer, sealer)
                writer.writeEnd()
                return
            }
            onAccepted()
            for (item in items) {
                if (item.flag == RelayChunk.FLAG_JOINABLE) {
                    needJoinable = false
                } else if (needJoinable) {
                    continue
                }
                val payload = if (sealer != null) sealer.media(item.bytes, item.flag) else item.bytes
                writer.write(item.flag, payload)
            }
            writer.writeEnd()
        }
    }

    /** Donor watch path: the upload is one listener of the same bounded LAN audio source. */
    private fun forwardSource(url: String, writer: RelayChunkWriter, sealer: ExchangeRelaySealer?) {
        val connection = (java.net.URL(url).openConnection() as java.net.HttpURLConnection).apply {
            connectTimeout = 5_000
            readTimeout = 5_000
        }
        source.set(connection)
        try {
            if (closed.get()) return
            connection.inputStream.use { input ->
                onAccepted()
                val splitter = AdtsFrameSplitter()
                val buffer = ByteArray(8 * 1024)
                while (!closed.get()) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    splitter.feed(buffer, count).forEach { frame ->
                        val payload = if (sealer != null) sealer.media(frame, RelayChunk.FLAG_JOINABLE) else frame
                        writer.write(RelayChunk.FLAG_JOINABLE, payload)
                    }
                }
            }
        } finally {
            source.compareAndSet(connection, null)
            connection.disconnect()
        }
    }

    /**
     * Lets go of this upload without ending the broadcast: a newer upload for the same broadcast
     * replaces it (14.5 item N), and a zero length sent now would end the broadcast under it.
     */
    fun abandon() {
        closed.set(true)
        source.getAndSet(null)?.disconnect()
        runCatching { session.getAndSet(null)?.close() }
        items.cancel()
        scope.cancel()
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        if (sourceUrl != null) {
            source.getAndSet(null)?.disconnect()
            runCatching { session.getAndSet(null)?.close() }
            scope.cancel()
        }
        // Closing the channel ends the chunk loop only after it drains, so the zero-length end
        // chunk follows every chunk the encoder already handed over; then the stream closes.
        items.close()
    }

    companion object {
        private const val KEY_BROADCAST_ID = "broadcastId"
        private const val KEY_CONTENT_TYPE = "contentType"

        /** One encoder frame per item: about 2.7 s of audio at 48 kHz. */
        const val FRAME_QUEUE_FRAMES = 128

        /** About a hundred milliseconds of video per item: two to three seconds of backlog at most. */
        const val VIDEO_QUEUE_CHUNKS = 32

        /** Five failures - about a minute of backoff - then the drop is visible and final. */
        private const val MAX_FAILURES = 5
        private val RETRY_BACKOFF_MS = longArrayOf(2_000, 5_000, 10_000, 20_000, 40_000)
    }
}
