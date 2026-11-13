package com.sza.streamsplayer.domain.usecase.streams

import com.sza.streamsplayer.core.network.NetworkContextAnalyzer
import com.sza.streamsplayer.data.repository.StreamCatalogCsvParser
import com.sza.streamsplayer.data.repository.StreamCollectionRepository
import com.sza.streamsplayer.data.repository.StreamCollectionsJsonParser
import com.sza.streamsplayer.data.repository.StreamSourceRepository
import com.sza.streamsplayer.data.repository.streams.FaviconAtlasStore
import com.sza.streamsplayer.data.streams.StreamCatalogFacetNormalizer
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
class ImportStreamCatalogUseCaseTest {
    private val repository = mockk<StreamSourceRepository>(relaxed = true)
    private val atlas = mockk<FaviconAtlasStore>(relaxed = true)
    private val collections = mockk<StreamCollectionRepository>(relaxed = true)
    private val network = mockk<NetworkContextAnalyzer> {
        every { hasAnyNetwork() } returns true
    }

    private fun useCase(client: OkHttpClient = OkHttpClient()) = ImportStreamCatalogUseCase(
        client, StreamCatalogCsvParser(), StreamCatalogFacetNormalizer(), StreamMediaKindClassifier(),
        repository, atlas, StreamCollectionsJsonParser(), collections, network, mockk(relaxed = true)
    )

    @Test
    fun `old ZIP without optional entries still yields its CSV`() = runTest {
        val payload = useCase().extractCatalog(ByteArrayInputStream(zip("streams.csv" to CSV.toByteArray())))!!
        assertEquals(CSV, payload.csv)
        assertEquals(null, payload.atlasPng)
        assertEquals(null, payload.collectionsJson)
    }

    @Test
    fun `unknown entries are skipped and artwork comes from the same ZIP`() = runTest {
        val bytes = zip(
            "streams.csv" to CSV.toByteArray(), "future.json" to "{}".toByteArray(),
            "favicon-atlas.png" to byteArrayOf(1, 2, 3),
            "collections.json" to "{\"schemaVersion\":1,\"collections\":[]}".toByteArray()
        )
        val payload = useCase().extractCatalog(ByteArrayInputStream(bytes))!!
        assertEquals(CSV, payload.csv)
        assertTrue(payload.atlasPng!!.contentEquals(byteArrayOf(1, 2, 3)))
        assertTrue(payload.collectionsJson!!.contains("schemaVersion"))
    }

    @Test
    fun `a publish-window 404 is retried before one catalog merge`() = runTest {
        val calls = AtomicInteger()
        coEvery { repository.mergeCatalog(any()) } returns StreamSourceRepository.CatalogMergeResult(1, 0, 0)
        val client = client {
            if (calls.incrementAndGet() == 1) 404 to byteArrayOf() else 200 to zip("streams.csv" to CSV.toByteArray())
        }

        assertEquals(ImportStreamCatalogUseCase.CatalogImportResult.Success(1, 0, 0), useCase(client)())
        assertEquals(2, calls.get())
        coVerify(exactly = 1) { repository.mergeCatalog(any()) }
    }

    @Test
    fun `other HTTP failures preserve the bank without retrying`() = runTest {
        val calls = AtomicInteger()
        val result = useCase(client { calls.incrementAndGet(); 500 to byteArrayOf() })()

        assertEquals(ImportStreamCatalogUseCase.CatalogImportResult.Failure("HTTP 500"), result)
        assertEquals(1, calls.get())
        coVerify(exactly = 0) { repository.mergeCatalog(any()) }
        coVerify(exactly = 0) { atlas.write(any(), any()) }
        coVerify(exactly = 0) { collections.replaceAll(any()) }
    }

    @Test
    fun `an unavailable network never fetches or mutates the bank`() = runTest {
        every { network.hasAnyNetwork() } returns false
        val calls = AtomicInteger()
        val result = useCase(client { calls.incrementAndGet(); 200 to byteArrayOf() })()

        assertEquals(ImportStreamCatalogUseCase.CatalogImportResult.Failure("no network"), result)
        assertEquals(0, calls.get())
        coVerify(exactly = 0) { repository.mergeCatalog(any()) }
    }

    private fun client(response: () -> Pair<Int, ByteArray>) = OkHttpClient.Builder()
        .addInterceptor { chain ->
            val (code, body) = response()
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(code).message("scripted response").body(body.toResponseBody()).build()
        }.build()

    private fun zip(vararg entries: Pair<String, ByteArray>): ByteArray {
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { zip ->
            entries.forEach { (name, body) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(body)
                zip.closeEntry()
            }
        }
        return bytes.toByteArray()
    }

    private companion object {
        const val CSV = "name,url,media_kind\nRadio,https://radio.example/live,AUDIO\n"
    }
}
