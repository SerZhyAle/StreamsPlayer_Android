package com.sza.streamsplayer.data.delivery

import com.sza.streamsplayer.domain.delivery.ArtworkManifestSource
import com.sza.streamsplayer.domain.delivery.DeliverableCapabilityRepository
import com.sza.streamsplayer.domain.delivery.DeliverableSet
import com.sza.streamsplayer.domain.delivery.DeliverableSourceDescriptor
import com.sza.streamsplayer.domain.delivery.DownloadProgress
import com.sza.streamsplayer.domain.delivery.PayloadFile
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * STREAM-BANK rules 8 and 11 on the phone's artwork download: a 404 inside the publish window is
 * retried, any other failure leaves what is installed alone, and nothing partial is ever promoted. The
 * inputs are generated here at run time; they stand in for the owner's vectors only until those arrive.
 */
class RealDeliverableSetDownloaderPublishWindowTest {

    @get:Rule
    val folder = TemporaryFolder()

    private lateinit var server: MockWebServer
    private lateinit var payloadDir: File
    private val set = DeliverableSet.STREAM_LOGO_ATLAS
    private val repository = mockk<DeliverableCapabilityRepository>(relaxed = true)

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        payloadDir = File(folder.root, "delivery/${set.name}")
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun downloader(): RealDeliverableSetDownloader {
        val manifest = mockk<DeliveryManifestDataSource>()
        coEvery { manifest.resolve(set) } returns DeliverableSourceDescriptor(
            set = set,
            files = listOf(
                PayloadFile("stream-logo-tiles.zip", listOf(server.url("/pack").toString()), "", minSize = 1),
                PayloadFile("stream-logo-coords.json", listOf(server.url("/coords").toString()), "", minSize = 1),
            ),
        )
        val markerStore = mockk<InstalledSetMarkerStore>()
        every { markerStore.payloadDir(set) } returns payloadDir
        val artworkManifest = mockk<ArtworkManifestSource>()
        coEvery { artworkManifest.stampOf(set) } returns STAMP
        return RealDeliverableSetDownloader(
            manifest = manifest,
            verifier = PayloadIntegrityVerifier(),
            markerStore = markerStore,
            repository = repository,
            artworkManifest = artworkManifest,
            okHttpClient = OkHttpClient(),
        ).apply { publishRetryDelayMillis = 1L }
    }

    private fun packResponse() = MockResponse().setBody(Buffer().write(tilePackBytes()))

    private fun tilePackBytes(): ByteArray {
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { zip ->
            zip.putNextEntry(ZipEntry("0"))
            zip.write(ByteArray(64))
            zip.closeEntry()
        }
        return bytes.toByteArray()
    }

    @Test
    fun `a 404 during the publish window is retried and the payload installs`() = runTest {
        server.enqueue(MockResponse().setResponseCode(404))
        server.enqueue(packResponse())
        server.enqueue(MockResponse().setBody("""{"http://a":0}"""))

        val progress = downloader().download(set).toList()

        assertEquals(DownloadProgress.Installed, progress.last())
        assertTrue(File(payloadDir, "stream-logo-tiles.zip").isFile)
        coVerify(exactly = 1) { repository.markInstalled(set, STAMP) }
    }

    @Test
    fun `a 404 that outlasts the window fails without touching what is installed`() = runTest {
        File(payloadDir, "stream-logo-tiles.zip").apply { parentFile?.mkdirs(); writeText("held") }
        repeat(MAX_ATTEMPTS) { server.enqueue(MockResponse().setResponseCode(404)) }

        val progress = downloader().download(set).toList()

        assertTrue(progress.last() is DownloadProgress.Failed)
        assertEquals("held", File(payloadDir, "stream-logo-tiles.zip").readText())
        assertFalse("no staging dir is left behind", File(folder.root, "delivery/${set.name}.tmp").exists())
    }

    @Test
    fun `a status other than 404 is not retried`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500))

        val progress = downloader().download(set).toList()

        assertTrue(progress.last() is DownloadProgress.Failed)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `a body that is not a tile pack is refused and nothing is promoted`() = runTest {
        server.enqueue(MockResponse().setBody("<html>not a zip</html>"))

        val progress = downloader().download(set).toList()

        assertTrue(progress.last() is DownloadProgress.Failed)
        assertFalse(payloadDir.exists())
    }

    private companion object {
        const val STAMP = "stamp-under-test"
        const val MAX_ATTEMPTS = 4
    }
}
