package com.sza.streamsplayer.data.delivery

import androidx.annotation.VisibleForTesting
import com.sza.streamsplayer.domain.delivery.ArtworkManifestSource
import com.sza.streamsplayer.domain.delivery.DeliverableCapabilityRepository
import com.sza.streamsplayer.domain.delivery.DeliverableSet
import com.sza.streamsplayer.domain.delivery.DeliverableSetDownloader
import com.sza.streamsplayer.domain.delivery.DownloadProgress
import com.sza.streamsplayer.domain.delivery.PayloadFile
import com.sza.streamsplayer.domain.streams.facets.StreamBankFetchRetry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Downloads a set's payload trying each file's ordered sources until one responds and verifies,
 * staging into a sibling `<set>.tmp` directory, then atomically promoting it to
 * `filesDir/delivery/<set>/` and flipping the install marker (S0386 strategic Pillar C, ADR-3).
 *
 * Failure is all-or-nothing: any unrecoverable source/verification failure deletes the staging
 * directory and emits [DownloadProgress.Failed] without leaving a partial payload or a marker.
 */
@Singleton
class RealDeliverableSetDownloader @Inject constructor(
    private val manifest: DeliveryManifestDataSource,
    private val verifier: PayloadIntegrityVerifier,
    private val markerStore: InstalledSetMarkerStore,
    private val repository: DeliverableCapabilityRepository,
    private val artworkManifest: ArtworkManifestSource,
    okHttpClient: OkHttpClient
) : DeliverableSetDownloader {

    // Mirror the 15 s connect/read timeouts of the shared client; no overall call timeout so
    // large payloads are not cut off mid-transfer.
    private val downloadClient: OkHttpClient = okHttpClient.newBuilder()
        .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    /** The wait of the publish-window retry; a test shortens it, because the flow runs on real time. */
    @VisibleForTesting
    internal var publishRetryDelayMillis: Long = StreamBankFetchRetry.RETRY_DELAY_MILLIS

    override fun download(set: DeliverableSet): Flow<DownloadProgress> = downloadFromSources(set)

    private fun downloadFromSources(set: DeliverableSet): Flow<DownloadProgress> {
        return flow {
            emit(DownloadProgress.Queued)

            val descriptor = manifest.resolve(set)
            if (descriptor == null || descriptor.files.isEmpty()) {
                emit(DownloadProgress.Failed("no descriptor for $set"))
                return@flow
            }

            val payloadDir = markerStore.payloadDir(set)
            val stagingDir = File(payloadDir.parentFile, "${set.name}.tmp")
            stagingDir.deleteRecursively()
            if (!stagingDir.mkdirs()) {
                emit(DownloadProgress.Failed("cannot create staging dir for $set"))
                return@flow
            }

            // `finally` deletes the staging dir on ANY non-promoted exit - failure, early return, or
            // coroutine cancellation (collector closed mid-download) - so a cancelled download never
            // leaves an orphaned `<set>.tmp` in filesDir. After a successful promote the staging dir
            // is gone (renamed), so `promoted` skips the cleanup.
            var promoted = false
            try {
                val totalEstimate = descriptor.files.sumOf { it.minSize }.coerceAtLeast(1L)
                var completedBytes = 0L

                for (file in descriptor.files) {
                    val staged = File(stagingDir, "${file.fileName}.tmp")
                    val ok = downloadWithFailover(file, staged, completedBytes, totalEstimate)
                    if (!ok) {
                        emit(DownloadProgress.Failed("all sources failed for ${file.fileName}"))
                        return@flow
                    }

                    emit(DownloadProgress.Verifying)
                    when (val result = verifier.verify(staged, file)) {
                        is PayloadIntegrityVerifier.Result.Failed -> {
                            emit(DownloadProgress.Failed(result.reason))
                            return@flow
                        }
                        PayloadIntegrityVerifier.Result.Verified -> {
                            val finalFile = File(stagingDir, file.fileName)
                            if (!staged.renameTo(finalFile)) {
                                emit(DownloadProgress.Failed("cannot finalize ${file.fileName}"))
                                return@flow
                            }
                            completedBytes += finalFile.length()
                        }
                    }
                }

                if (!promote(stagingDir, payloadDir)) {
                    emit(DownloadProgress.Failed("cannot install payload for $set"))
                    return@flow
                }
                promoted = true

                // S3827: an artwork set is judged against the published manifest's stamp (S1483). Recording it
                // here, not in the Extensions screen, keeps a download that finished after the screen closed
                // from reading as a permanent "update available".
                repository.markInstalled(set, artworkManifest.stampOf(set) ?: descriptor.stamp)
                emit(DownloadProgress.Installed)
            } finally {
                if (!promoted) stagingDir.deleteRecursively()
            }
        }.flowOn(Dispatchers.IO)
    }

    /** Try each source URL in order; first one that fully downloads to [dest] wins. */
    private suspend fun FlowCollector<DownloadProgress>.downloadWithFailover(
        file: PayloadFile,
        dest: File,
        baseBytes: Long,
        totalEstimate: Long
    ): Boolean {
        for (url in file.sources) {
            try {
                if (downloadOne(url, dest, baseBytes, totalEstimate)) {
                    return true
                }
                Timber.w("Delivery source returned no payload: %s", url)
            } catch (e: IOException) {
                Timber.w(e, "Delivery source failed, trying next: %s", url)
            }
            dest.delete()
        }
        return false
    }

    /**
     * STREAM-BANK rule 11: publishing replaces an asset in place, so a 404 for a short window is
     * expected and retried through [StreamBankFetchRetry], which is cancellable. Any other status is a
     * real answer and goes to the next source at once.
     */
    private suspend fun FlowCollector<DownloadProgress>.downloadOne(
        url: String,
        dest: File,
        baseBytes: Long,
        totalEstimate: Long
    ): Boolean {
        val request = Request.Builder().url(url).build()
        val outcome = StreamBankFetchRetry.fetch<Boolean>(
            delayMillis = publishRetryDelayMillis,
            onRetry = { attempt -> Timber.i("Delivery source 404 on attempt %d, retrying: %s", attempt, url) }
        ) {
            downloadClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body
                    if (body == null) {
                        StreamBankFetchRetry.Attempt.Done(false)
                    } else {
                        writeBody(body.byteStream(), dest, baseBytes, totalEstimate)
                        StreamBankFetchRetry.Attempt.Done(true)
                    }
                } else {
                    Timber.w("Delivery source HTTP %d: %s", response.code, url)
                    StreamBankFetchRetry.Attempt.HttpStatus(response.code)
                }
            }
        }
        return outcome is StreamBankFetchRetry.Attempt.Done && outcome.value
    }

    private suspend fun FlowCollector<DownloadProgress>.writeBody(
        source: java.io.InputStream,
        dest: File,
        baseBytes: Long,
        totalEstimate: Long
    ) {
        source.use { input ->
            dest.outputStream().use { output ->
                val buffer = ByteArray(BUFFER_SIZE)
                var fileBytes = 0L
                // Each emission becomes a notification update plus a WorkManager setProgress
                // (a Room write) in the worker; per-buffer emits made thousands of both per payload.
                var lastPercent = -1
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    output.write(buffer, 0, read)
                    fileBytes += read
                    // STREAM-BANK rule 8: stop at the ceiling instead of filling filesDir first and
                    // refusing afterwards; the failover loop treats this like any other dead source.
                    if (fileBytes > PayloadIntegrityVerifier.MAX_UNVERIFIED_RESOURCE_BYTES) {
                        throw IOException("payload over the ${PayloadIntegrityVerifier.MAX_UNVERIFIED_RESOURCE_BYTES} byte ceiling")
                    }
                    val cumulative = baseBytes + fileBytes
                    val percent = ((cumulative * 100) / totalEstimate).toInt().coerceIn(0, 99)
                    if (percent != lastPercent) {
                        lastPercent = percent
                        emit(DownloadProgress.Running(percent, cumulative, totalEstimate))
                    }
                }
            }
        }
    }

    /**
     * Replace [payloadDir] with the verified [stagingDir] without destroying the prior payload until
     * the new one is in place: move the old payload aside to a `.bak`, rename staging in, then drop
     * the backup. If the final rename fails, restore the backup so a re-download keeps the previous
     * working payload instead of being left with nothing. Renames within `filesDir` are atomic.
     */
    // S1703: a retired payload file is left where it is rather than hunted down. Dropping an entry from a
    // descriptor changes that set's stamp, so a device holding the old payload reports UpdateAvailable and
    // its next download lands here, where the whole directory is replaced - the retired file goes with it.
    // Verification never sees the stale file either: the loop above verifies the descriptor's own staged
    // files, so an extra one on disk cannot make a user's payload look damaged.
    private fun promote(stagingDir: File, payloadDir: File): Boolean {
        payloadDir.parentFile?.mkdirs()
        val backup = File(payloadDir.parentFile, "${payloadDir.name}.bak")
        backup.deleteRecursively()

        val movedOldAside = payloadDir.exists() && payloadDir.renameTo(backup)
        // If the old payload existed but could not be moved aside, remove it in place (best effort).
        if (payloadDir.exists()) payloadDir.deleteRecursively()

        if (stagingDir.renameTo(payloadDir)) {
            backup.deleteRecursively()
            return true
        }
        // Promotion failed - restore the previous payload if we had set it aside.
        if (movedOldAside) backup.renameTo(payloadDir)
        return false
    }

    private companion object {
        const val TIMEOUT_SECONDS = 15L
        const val BUFFER_SIZE = 8192
    }
}
