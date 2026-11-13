package com.sza.streamsplayer.data.exchange.publish

import java.io.ByteArrayOutputStream

/**
 * Cuts the transport-stream packets of [MpegTsMuxer] into the chunks of the relay upload
 * (DEVICE-EXCHANGE 7.6): the header chunk, a joinable chunk that starts with each IDR access unit, and
 * ordinary chunks for the rest of the group of pictures.
 *
 * A group of pictures is never held back whole - that would add its length to the delay - so a
 * chunk goes out as soon as it holds [FLUSH_BYTES] or [FLUSH_INTERVAL_US] of media, and at the next
 * keyframe. Only a chunk that opens with a keyframe is flagged joinable. A chunk never passes the
 * 1 MiB bound of item U: a unit that would is split at a packet boundary, and only the piece that
 * carries the keyframe's start stays joinable.
 *
 * The chunker is a pure function of what it is handed; timestamps are the caller's media clock.
 */
class TsRelayChunker(private val sink: Sink) {

    /** Receives the finished chunks in stream order. [flag] is one of the flags of [RelayChunk]. */
    fun interface Sink {
        fun chunk(flag: Int, payload: ByteArray)
    }

    private val pending = ByteArrayOutputStream(FLUSH_BYTES * 2)
    private var pendingJoinable = false
    private var pendingSinceUs = 0L
    private var lastKeyFrameUs = NONE

    /** Hands over the header chunk: PAT and PMT, replayed by the server to every new listener. */
    fun header(packets: ByteArray) {
        sink.chunk(RelayChunk.FLAG_HEADER, packets)
    }

    /** The packets of one video access unit; [keyFrame] opens a joinable chunk. */
    @Synchronized
    fun video(packets: ByteArray, ptsUs: Long, keyFrame: Boolean) {
        if (keyFrame) {
            flushPending()
            pendingJoinable = true
            lastKeyFrameUs = ptsUs
        }
        append(packets, ptsUs)
    }

    /** The packets of one audio frame, riding whichever chunk is open. */
    @Synchronized
    fun audio(packets: ByteArray, ptsUs: Long) {
        if (packets.isEmpty()) return
        append(packets, ptsUs)
    }

    /** Sends what is open; the end of the broadcast calls it before the zero length. */
    @Synchronized
    fun flush() {
        flushPending()
    }

    /**
     * True when no keyframe has been seen for [KEYFRAME_DEADLINE_US]: the encoder is asked for one,
     * so the "at least every 2 s" of 7.6 holds even when a camera runs slow and the encoder's own
     * frame count stretches the interval. A frame the encoder was asked for resets the clock.
     */
    @Synchronized
    fun keyFrameOverdue(ptsUs: Long): Boolean =
        lastKeyFrameUs != NONE && ptsUs - lastKeyFrameUs >= KEYFRAME_DEADLINE_US

    private fun append(packets: ByteArray, ptsUs: Long) {
        if (pending.size() == 0) pendingSinceUs = ptsUs
        var offset = 0
        while (offset < packets.size) {
            val room = RelayChunk.MAX_PAYLOAD - pending.size()
            if (room < MpegTsMuxer.PACKET_SIZE) {
                flushPending()
                continue
            }
            // Whole packets only: a chunk that ends mid-packet would break a listener that joins on it.
            val take = minOf(packets.size - offset, room / MpegTsMuxer.PACKET_SIZE * MpegTsMuxer.PACKET_SIZE)
            pending.write(packets, offset, take)
            offset += take
            if (offset < packets.size) flushPending()
        }
        if (pending.size() >= FLUSH_BYTES || ptsUs - pendingSinceUs >= FLUSH_INTERVAL_US) flushPending()
    }

    private fun flushPending() {
        if (pending.size() == 0) return
        val flag = if (pendingJoinable) RelayChunk.FLAG_JOINABLE else RelayChunk.FLAG_ORDINARY
        val bytes = pending.toByteArray()
        pending.reset()
        pendingJoinable = false
        sink.chunk(flag, bytes)
    }

    companion object {
        /** A chunk goes out once it holds this much. */
        const val FLUSH_BYTES = 32 * 1024

        /** ... or this much media time, which bounds the delay the chunking itself adds. */
        const val FLUSH_INTERVAL_US = 100_000L

        /** A keyframe is asked for after this long without one; the bound of 7.6 is 2 s. */
        const val KEYFRAME_DEADLINE_US = 1_800_000L

        private const val NONE = Long.MIN_VALUE
    }
}
