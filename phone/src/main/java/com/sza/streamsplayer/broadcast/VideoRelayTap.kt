package com.sza.streamsplayer.broadcast

import android.media.MediaCodec
import android.media.MediaFormat
import com.pedro.library.base.recording.BaseRecordController
import com.pedro.library.base.recording.RecordController
import com.sza.streamsplayer.data.exchange.publish.AdtsFrame
import com.sza.streamsplayer.data.exchange.publish.MpegTsMuxer
import com.sza.streamsplayer.data.exchange.publish.TsRelayChunker
import timber.log.Timber
import java.io.FileDescriptor
import java.nio.ByteBuffer

/**
 * Taps the camera session's encoders for the video relay upload (S0021 scope 5): the one encoder
 * output that feeds the RTSP server also feeds [MpegTsMuxer] here, so the LAN and the relay carry the
 * same pictures (LIVE-BROADCAST item C).
 *
 * The library offers a hook for exactly this - a pluggable record controller that receives every
 * encoded buffer - and this is one. It never records to a file and never reports itself as recording
 * or running, because the library reads both to decide whether to start and stop its encoders; to
 * the camera it is a controller with nothing to do. It is installed before the stream starts, so the
 * encoder's format announcement (the SPS and PPS a joining listener needs in front of each keyframe)
 * is not missed.
 *
 * The thin Android half only copies buffers out of the codec; every byte of the format is written by
 * the muxer and the chunker, which the JVM tests exercise. Timestamps are the encoders' own clock, the
 * one the RTSP packetizer uses.
 */
class VideoRelayTap(
    hasAudio: Boolean,
    private val sampleRateHz: Int,
    private val channelCount: Int,
    sink: TsRelayChunker.Sink,
    /** Asks the video encoder for a sync frame: when a keyframe is overdue, and when an upload starts. */
    private val requestSyncFrame: () -> Unit,
) : BaseRecordController() {

    private val hasAudio = hasAudio && AdtsFrame.supports(sampleRateHz)
    private val muxer = MpegTsMuxer(this.hasAudio)
    private val chunker = TsRelayChunker(sink)

    @Volatile
    private var active = false

    @Volatile
    private var parameterSets: ByteArray? = null

    private var lastSyncRequestUs = -SYNC_REQUEST_GAP_US
    private var warnedNoParameterSets = false
    private var failures = 0

    /** PAT and PMT: the header chunk of every upload stream. */
    val headerPackets: ByteArray
        get() = muxer.headerPackets()

    /** Starts feeding the chunker; frames before this, or after [stop], are ignored. */
    fun start() {
        active = true
    }

    /** Sends the chunk that is open and goes quiet; the publisher ends the upload after it. */
    fun stop() {
        active = false
        chunker.flush()
    }

    /** The relay accepted an upload stream: the first picture a listener can start at is due now. */
    fun onUploadAccepted() {
        requestSyncFrame()
    }

    override fun setVideoFormat(videoFormat: MediaFormat) {
        val sps = videoFormat.getByteBuffer(CSD_SPS)?.toBytes()
        val pps = videoFormat.getByteBuffer(CSD_PPS)?.toBytes()
        if (sps != null && pps != null) parameterSets = sps + pps
    }

    override fun setAudioFormat(audioFormat: MediaFormat) = Unit

    // The library calls this before it hands the same buffer to the RTSP server: whatever goes wrong
    // here must stay here, or the LAN broadcast would fall with the relay.
    @Suppress("TooGenericExceptionCaught")
    override fun recordVideo(videoBuffer: ByteBuffer, videoInfo: MediaCodec.BufferInfo) {
        try {
            feedVideo(videoBuffer, videoInfo)
        } catch (e: Exception) {
            reportFailure(e)
        }
    }

    @Suppress("TooGenericExceptionCaught")
    override fun recordAudio(audioBuffer: ByteBuffer, audioInfo: MediaCodec.BufferInfo) {
        try {
            feedAudio(audioBuffer, audioInfo)
        } catch (e: Exception) {
            reportFailure(e)
        }
    }

    private fun reportFailure(e: Exception) {
        if (failures++ < MAX_LOGGED_FAILURES) Timber.w(e, "S0021: relay feed failed, the LAN broadcast is unaffected")
    }

    private fun feedVideo(videoBuffer: ByteBuffer, videoInfo: MediaCodec.BufferInfo) {
        if (!active || videoInfo.size <= 0 || videoInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) return
        val keyFrame = videoInfo.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME != 0
        if (keyFrame && parameterSets == null && !warnedNoParameterSets) {
            warnedNoParameterSets = true
            Timber.w("S0021: keyframe before the encoder announced its SPS and PPS")
        }
        val pts = videoInfo.presentationTimeUs
        chunker.video(muxer.videoAccessUnit(copy(videoBuffer, videoInfo), pts, keyFrame, parameterSets), pts, keyFrame)
        if (chunker.keyFrameOverdue(pts) && pts - lastSyncRequestUs >= SYNC_REQUEST_GAP_US) {
            lastSyncRequestUs = pts
            requestSyncFrame()
        }
    }

    private fun feedAudio(audioBuffer: ByteBuffer, audioInfo: MediaCodec.BufferInfo) {
        if (!active || !hasAudio || audioInfo.size <= 0 ||
            audioInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0
        ) {
            return
        }
        val frame = AdtsFrame.wrap(copy(audioBuffer, audioInfo), sampleRateHz, channelCount)
        val pts = audioInfo.presentationTimeUs
        chunker.audio(muxer.audioFrame(frame, pts), pts)
    }

    override fun resetFormats() {
        parameterSets = null
    }

    // The camera reads these two to decide whether it owns its encoders; this tap never does.
    override fun isRunning(): Boolean = false

    override fun isRecording(): Boolean = false

    override fun startRecord(
        path: String,
        listener: RecordController.Listener?,
        tracks: RecordController.RecordTracks,
    ) = Unit

    override fun startRecord(
        fd: FileDescriptor,
        listener: RecordController.Listener?,
        tracks: RecordController.RecordTracks,
    ) = Unit

    override fun stopRecord() = Unit

    private fun copy(buffer: ByteBuffer, info: MediaCodec.BufferInfo): ByteArray {
        val view = buffer.duplicate()
        view.position(info.offset)
        view.limit(info.offset + info.size)
        return ByteArray(info.size).also(view::get)
    }

    private fun ByteBuffer.toBytes(): ByteArray = duplicate().let { view ->
        view.rewind()
        ByteArray(view.remaining()).also(view::get)
    }

    private companion object {
        const val CSD_SPS = "csd-0"
        const val CSD_PPS = "csd-1"

        /** Not a request per picture: the encoder needs a moment to answer one. */
        const val SYNC_REQUEST_GAP_US = 500_000L

        /** A feed that keeps failing logs its first few failures, not one per picture. */
        const val MAX_LOGGED_FAILURES = 3
    }
}
