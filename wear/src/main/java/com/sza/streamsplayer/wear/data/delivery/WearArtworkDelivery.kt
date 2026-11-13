package com.sza.streamsplayer.wear.data.delivery

import androidx.annotation.VisibleForTesting
import com.sza.streamsplayer.domain.streams.facets.StreamBankFetchRetry
import com.sza.streamsplayer.wear.data.repository.WearArtworkStore
import com.sza.streamsplayer.wear.domain.delivery.ArtworkManifestSet
import com.sza.streamsplayer.wear.domain.delivery.DeliverableSet
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S0050: the watch fetches its own artwork, on the triggers its catalog import already has. Nothing
 * arrives from the phone over the Data Layer (owner decision of 2026-11-08).
 *
 * Adapted from the phone's `RealDeliverableSetDownloader`: the same staging, verification and
 * all-or-nothing promotion, without WorkManager, because the watch has no offer to answer and no
 * notification to show. A refresh polls the manifest, and a set is fetched only when its `stamp` differs
 * from what is installed (STREAM-BANK rule 12). Every failure keeps the artwork already held.
 *
 * S0064: the wearer can remove a set and fetch it again from the watch's Channel pictures screen
 * ([remove], [download]). A removed set stays in [WearArtworkOptOutStore] until Download, so the
 * refresh does not bring it back. This has no twin in the phone's downloader.
 */
@Singleton
class WearArtworkDelivery @Inject constructor(
    okHttpClient: OkHttpClient,
    private val manifestClient: ArtworkManifestClient,
    private val verifier: PayloadIntegrityVerifier,
    private val store: WearArtworkStore,
    // S0064: the sets the wearer removed on purpose; a refresh leaves them alone.
    private val optOut: WearArtworkOptOutStore,
) {
    // A finite deadline per file: a host that trickles bytes resets the read timeout on every chunk.
    private val downloadClient: OkHttpClient = okHttpClient.newBuilder()
        .callTimeout(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    // One refresh at a time: a second pull while the first still downloads would share its staging dir.
    private val running = Mutex()

    // S0064: promoting a payload and deleting one never interleave, so a removal cannot be followed by a
    // promotion that read the opt-out a moment too early.
    private val swap = Mutex()

    @VisibleForTesting
    internal var mirrorBaseUrl: String = MIRROR

    /** The wait of the publish-window retry; a test shortens it, because the download runs on real time. */
    @VisibleForTesting
    internal var publishRetryDelayMillis: Long = StreamBankFetchRetry.RETRY_DELAY_MILLIS

    /**
     * Brings the installed artwork up to the published manifest. [onInstalled] runs after each set that
     * landed, so the list can repaint without waiting for the other one. Cancellation stops the refresh
     * and removes its staging directory; a refresh already in progress makes this call a no-op.
     *
     * S0064: a set the wearer removed is skipped, and when every set is removed the mirror is not asked
     * at all.
     */
    suspend fun refresh(onInstalled: suspend (DeliverableSet) -> Unit = {}) {
        if (!running.tryLock()) return
        try {
            withContext(Dispatchers.IO) {
                val removed = optOut.optedOut()
                val wanted = REFRESH_ORDER - removed
                if (wanted.size < REFRESH_ORDER.size) {
                    Timber.d("S0064: watch refresh leaves out the removed sets")
                }
                if (wanted.isEmpty()) return@withContext
                val manifest = manifestClient.fetch() ?: return@withContext
                for (set in wanted) {
                    val entry = manifest.sets[set] ?: continue
                    if (entry.stamp == store.installedStamp(set)) continue
                    if (install(set, entry)) {
                        Timber.i("Wear artwork: %s installed, stamp %s", set, entry.stamp)
                        onInstalled(set)
                    }
                }
            }
        } finally {
            running.unlock()
        }
    }

    /**
     * S0064: the wearer's own Download. Takes [set] out of the removed sets, so refresh fetches it again
     * from now on, and fetches it now. True when the set is installed at the published stamp afterwards;
     * false when the mirror gave no answer or the payload did not verify, with what is held kept.
     * Waits for a refresh in progress instead of skipping, because the wearer asked for this one.
     */
    suspend fun download(set: DeliverableSet): Boolean {
        Timber.d("S0064: watch channel pictures download tapped, set %s", set)
        optOut.setOptedOut(set, false)
        return running.withLock {
            withContext(Dispatchers.IO) {
                val entry = manifestClient.fetch()?.sets?.get(set) ?: return@withContext false
                if (entry.stamp == store.installedStamp(set)) return@withContext true
                install(set, entry).also { installed ->
                    if (installed) Timber.i("Wear artwork: %s installed on request, stamp %s", set, entry.stamp)
                }
            }
        }
    }

    /**
     * S0064: the wearer's Remove. Records the removal first and deletes second: a process that dies
     * between the two leaves a copy the next Remove deletes, where the other order would leave a deleted
     * copy that the next pull to refresh quietly brings back. Not cancellable, so leaving the screen
     * cannot stop it half done. A download of the set in flight finds the record before it promotes.
     */
    suspend fun remove(set: DeliverableSet) {
        Timber.d("S0064: watch channel pictures remove confirmed, set %s", set)
        withContext(NonCancellable + Dispatchers.IO) {
            optOut.setOptedOut(set, true)
            val deleted = swap.withLock { store.delete(set) }
            if (!deleted) Timber.w("Wear artwork: %s could not be fully deleted", set)
        }
    }

    private suspend fun install(set: DeliverableSet, entry: ArtworkManifestSet): Boolean {
        // STREAM-BANK rule 8: an atlas over the consumer's cap degrades to no new tile. The manifest
        // gave the number before any byte moved, and what is installed stays.
        if (entry.totalBytes > PayloadIntegrityVerifier.MAX_SET_BYTES) {
            Timber.w("Wear artwork: %s is %d bytes, over the ceiling - not fetched", set, entry.totalBytes)
            return false
        }
        val stagingDir = store.stagingDir(set)
        stagingDir.deleteRecursively()
        if (!stagingDir.mkdirs()) {
            Timber.w("Wear artwork: cannot create staging dir for %s", set)
            return false
        }
        // `finally` deletes the staging dir on ANY non-promoted exit - failure, early return, or
        // coroutine cancellation - so a cancelled download never leaves an orphaned `<set>.tmp`.
        var promoted = false
        try {
            if (!fetchFile(stagingDir, WearArtworkStore.packName(set), PACK_MIN_SIZE)) return false
            // S0064: the wearer may remove the set while it downloads; stop before the second file.
            if (optOut.isOptedOut(set)) return false
            if (!fetchFile(stagingDir, WearArtworkStore.coordsName(set), COORDS_MIN_SIZE)) return false
            store.writeStamp(stagingDir, entry.stamp)
            // S0064: read again under the lock a removal deletes under, so a removal made after this
            // point finds the promoted payload and deletes it, and one made before it is seen here.
            promoted = swap.withLock { !optOut.isOptedOut(set) && store.promote(set, stagingDir) }
            return promoted
        } catch (e: IOException) {
            // No network, a dead socket, a full disk: nothing new this time, and what is installed stays.
            Timber.w(e, "Wear artwork: %s not fetched", set)
            return false
        } catch (e: CancellationException) {
            throw e
        } catch (e: IllegalStateException) {
            // OkHttp raises this for a call reused or closed out of order; an escape would end the watch app.
            Timber.w(e, "Wear artwork: %s not fetched", set)
            return false
        } finally {
            if (!promoted) stagingDir.deleteRecursively()
        }
    }

    private suspend fun fetchFile(stagingDir: File, fileName: String, minSize: Long): Boolean {
        val staged = File(stagingDir, "$fileName.tmp")
        if (!download("$mirrorBaseUrl/$fileName", staged)) return false
        if (verifier.verify(staged, fileName, minSize) !is PayloadIntegrityVerifier.Result.Verified) return false
        return staged.renameTo(File(stagingDir, fileName))
    }

    /**
     * STREAM-BANK rule 11: publishing replaces an asset in place, so a 404 for a short window is
     * expected and retried through [StreamBankFetchRetry], which is cancellable. Any other status is a
     * real answer and ends this refresh for the set.
     */
    private suspend fun download(url: String, dest: File): Boolean {
        val request = Request.Builder().url(url).build()
        val outcome = StreamBankFetchRetry.fetch<Boolean>(
            delayMillis = publishRetryDelayMillis,
            onRetry = { attempt -> Timber.i("Wear artwork: 404 on attempt %d, retrying: %s", attempt, url) }
        ) {
            downloadClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body
                    if (body == null) {
                        StreamBankFetchRetry.Attempt.Done(false)
                    } else {
                        writeCapped(body.byteStream(), dest)
                        StreamBankFetchRetry.Attempt.Done(true)
                    }
                } else {
                    Timber.w("Wear artwork: HTTP %d for %s", response.code, url)
                    StreamBankFetchRetry.Attempt.HttpStatus(response.code)
                }
            }
        }
        return outcome is StreamBankFetchRetry.Attempt.Done && outcome.value
    }

    private suspend fun writeCapped(source: InputStream, dest: File) {
        source.use { input ->
            dest.outputStream().use { output ->
                val buffer = ByteArray(BUFFER_SIZE)
                var written = 0L
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val read = input.read(buffer)
                    if (read < 0) break
                    output.write(buffer, 0, read)
                    written += read
                    // The manifest's number is not a promise about the body: stop at the ceiling
                    // instead of filling the watch's storage first and refusing afterwards.
                    if (written > PayloadIntegrityVerifier.MAX_SET_BYTES) {
                        throw IOException("payload over the ${PayloadIntegrityVerifier.MAX_SET_BYTES} byte ceiling")
                    }
                }
            }
        }
    }

    private companion object {
        const val MIRROR = "https://github.com/SerZhyAle/FastMediaSorter_mob_v2/releases/download/delivery-so-v1"

        // STREAM-BANK document 09 section 3: the floors a truncated download cannot pass.
        const val PACK_MIN_SIZE = 1_000_000L
        const val COORDS_MIN_SIZE = 32_768L

        // Logos first: they serve every channel kind and are the smaller download.
        val REFRESH_ORDER = listOf(DeliverableSet.STREAM_LOGO_ATLAS, DeliverableSet.CHANNEL_PREVIEW_ATLAS)

        // A watch usually pulls over a proxied phone link: the catalog import measured ~42 KB/s as the
        // floor to survive, and at that rate the 14.6 MB preview pack needs about 350 s.
        const val CALL_TIMEOUT_SECONDS = 420L
        const val BUFFER_SIZE = 8192
    }
}
