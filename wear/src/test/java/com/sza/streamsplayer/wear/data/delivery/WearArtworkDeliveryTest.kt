package com.sza.streamsplayer.wear.data.delivery

import android.app.Application
import com.sza.streamsplayer.wear.data.repository.WearArtworkStore
import com.sza.streamsplayer.wear.domain.delivery.DeliverableSet
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.random.Random

/**
 * The negative cases of STREAM-BANK rules 8, 11 and 12 on the watch: every one of them must keep what is
 * held. The inputs are generated here at run time; they stand in for the owner's vectors only until those
 * arrive. Robolectric supplies a real `org.json` and `filesDir`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class WearArtworkDeliveryTest {

    private val context = RuntimeEnvironment.getApplication()
    private val store = WearArtworkStore(context)
    private val optOut = WearArtworkOptOutStore(InMemoryPreferencesDataStore())
    private val logo = DeliverableSet.STREAM_LOGO_ATLAS
    private val preview = DeliverableSet.CHANNEL_PREVIEW_ATLAS
    private val deliveryRoot = File(context.filesDir, "delivery")

    private lateinit var server: MockWebServer
    private val answers = HashMap<String, MutableList<MockResponse>>()
    private val requested = CopyOnWriteArrayList<String>()

    @Before
    fun setUp() {
        deliveryRoot.deleteRecursively()
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                requested += path
                val queue = synchronized(answers) { answers[path] }
                return synchronized(answers) { queue?.removeFirstOrNull() } ?: MockResponse().setResponseCode(404)
            }
        }
        server.start()
    }

    @After
    fun tearDown() {
        runCatching { server.shutdown() }
    }

    private fun answer(path: String, vararg responses: MockResponse) {
        synchronized(answers) { answers.getOrPut(path) { mutableListOf() }.addAll(responses) }
    }

    private fun delivery(retryDelayMillis: Long = 1L): WearArtworkDelivery {
        val client = OkHttpClient()
        return WearArtworkDelivery(
            okHttpClient = client,
            manifestClient = ArtworkManifestClient(client).apply { manifestUrl = server.url(MANIFEST_PATH).toString() },
            verifier = PayloadIntegrityVerifier(),
            store = store,
            optOut = optOut,
        ).apply {
            mirrorBaseUrl = server.url("/").toString().trimEnd('/')
            publishRetryDelayMillis = retryDelayMillis
        }
    }

    private fun packBytes(seed: Int = 1): ByteArray {
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { zip ->
            zip.putNextEntry(ZipEntry("0"))
            // Incompressible, so the archive clears the 1 MB floor a truncated download cannot pass.
            zip.write(Random(seed).nextBytes(MIN_PACK_BYTES))
            zip.closeEntry()
        }
        return bytes.toByteArray()
    }

    private fun coordsBody(): String =
        (0 until COORDS_ROWS).joinToString(",", "{", "}") { "\"http://channel/$it\":0" }

    private fun manifest(logoStamp: String = "logo-1", logoSize: Long = 1L, schema: Int = 1, extraSet: String = ""): MockResponse =
        MockResponse().setBody(
            """{"schemaVersion":$schema,"generatedAt":"t","sets":{$extraSet"streamLogo":{"stamp":"$logoStamp","files":[{"name":"p","size":$logoSize}]}}}"""
        )

    private fun answerLogoPayload(seed: Int = 1) {
        answer(LOGO_PACK_PATH, MockResponse().setBody(Buffer().write(packBytes(seed))))
        answer(LOGO_COORDS_PATH, MockResponse().setBody(coordsBody()))
    }

    private suspend fun installLogo(stamp: String = "logo-1", seed: Int = 1): ByteArray {
        answer(MANIFEST_PATH, manifest(logoStamp = stamp))
        answerLogoPayload(seed)
        delivery().refresh()
        val pack = assertNotNullPack()
        assertEquals(stamp, store.installedStamp(logo))
        requested.clear()
        return pack.readBytes()
    }

    private fun assertNotNullPack(): File = requireNotNull(store.tilePackFile(logo)) { "logo pack was not installed" }

    private fun assertStaysHeld(held: ByteArray) {
        assertTrue("held artwork must stay", held.contentEquals(assertNotNullPack().readBytes()))
        assertFalse("no staging dir is left behind", File(deliveryRoot, "${logo.name}.tmp").exists())
    }

    @Test
    fun `a fresh install fetches the manifest, the pack and the coords`() = runTest {
        val installedSets = mutableListOf<DeliverableSet>()
        answer(MANIFEST_PATH, manifest())
        answerLogoPayload()

        delivery().refresh { installedSets += it }

        assertEquals(listOf(logo), installedSets)
        assertNotNull(store.tilePackFile(logo))
        assertEquals("logo-1", store.installedStamp(logo))
        assertEquals(COORDS_ROWS, store.coords(logo).size)
    }

    @Test
    fun `an absent manifest is nothing new`() = runTest {
        answer(MANIFEST_PATH, MockResponse().setResponseCode(404))

        delivery().refresh()

        assertEquals(listOf(MANIFEST_PATH), requested.toList())
        assertNull(store.tilePackFile(logo))
    }

    @Test
    fun `an unreachable manifest is nothing new and keeps what is held`() = runTest {
        val held = installLogo()
        val delivery = delivery()
        runCatching { server.shutdown() }

        delivery.refresh()

        assertStaysHeld(held)
    }

    @Test
    fun `an unparseable manifest is nothing new and keeps what is held`() = runTest {
        val held = installLogo()
        answer(MANIFEST_PATH, MockResponse().setBody("<html>not json</html>"))

        delivery().refresh()

        assertEquals(listOf(MANIFEST_PATH), requested.toList())
        assertStaysHeld(held)
    }

    @Test
    fun `a higher schema is refused whole and keeps what is held`() = runTest {
        val held = installLogo()
        answer(MANIFEST_PATH, manifest(logoStamp = "logo-2", schema = 2))

        delivery().refresh()

        assertEquals(listOf(MANIFEST_PATH), requested.toList())
        assertEquals("logo-1", store.installedStamp(logo))
        assertStaysHeld(held)
    }

    @Test
    fun `an unknown set is ignored`() = runTest {
        answer(MANIFEST_PATH, manifest(extraSet = """"thirdSet":{"stamp":"x","files":[]},"""))
        answerLogoPayload()

        delivery().refresh()

        assertNotNull(store.tilePackFile(logo))
        assertNull(store.tilePackFile(preview))
        assertFalse(requested.any { it.contains("third") })
    }

    @Test
    fun `the same stamp fetches nothing`() = runTest {
        installLogo()
        answer(MANIFEST_PATH, manifest())

        delivery().refresh()

        assertEquals(listOf(MANIFEST_PATH), requested.toList())
    }

    @Test
    fun `a new stamp replaces the payload`() = runTest {
        val held = installLogo(stamp = "logo-1", seed = 1)
        answer(MANIFEST_PATH, manifest(logoStamp = "logo-2"))
        answerLogoPayload(seed = 2)

        delivery().refresh()

        assertEquals("logo-2", store.installedStamp(logo))
        assertFalse(held.contentEquals(assertNotNullPack().readBytes()))
    }

    @Test
    fun `a set over the ceiling is not fetched and what is held stays`() = runTest {
        val held = installLogo()
        answer(MANIFEST_PATH, manifest(logoStamp = "logo-2", logoSize = PayloadIntegrityVerifier.MAX_SET_BYTES + 1))

        delivery().refresh()

        assertEquals(listOf(MANIFEST_PATH), requested.toList())
        assertEquals("logo-1", store.installedStamp(logo))
        assertStaysHeld(held)
    }

    @Test
    fun `a body that is not a tile pack is refused and what is held stays`() = runTest {
        val held = installLogo()
        answer(MANIFEST_PATH, manifest(logoStamp = "logo-2"))
        answer(LOGO_PACK_PATH, MockResponse().setBody("x".repeat(MIN_PACK_BYTES)))

        delivery().refresh()

        assertEquals("logo-1", store.installedStamp(logo))
        assertStaysHeld(held)
    }

    @Test
    fun `a sidecar failure applies nothing, the pack that already arrived included`() = runTest {
        val held = installLogo()
        answer(MANIFEST_PATH, manifest(logoStamp = "logo-2"))
        answer(LOGO_PACK_PATH, MockResponse().setBody(Buffer().write(packBytes(seed = 2))))
        answer(LOGO_COORDS_PATH, MockResponse().setResponseCode(500))

        delivery().refresh()

        assertEquals("logo-1", store.installedStamp(logo))
        assertStaysHeld(held)
    }

    @Test
    fun `a 404 during the publish window is retried and the payload installs`() = runTest {
        answer(MANIFEST_PATH, manifest())
        answer(LOGO_PACK_PATH, MockResponse().setResponseCode(404))
        answerLogoPayload()

        delivery().refresh()

        assertEquals("logo-1", store.installedStamp(logo))
    }

    @Test
    fun `a 404 that outlasts the window keeps what is held`() = runTest {
        val held = installLogo()
        answer(MANIFEST_PATH, manifest(logoStamp = "logo-2"))

        delivery().refresh()

        assertEquals(MAX_ATTEMPTS, requested.count { it == LOGO_PACK_PATH })
        assertEquals("logo-1", store.installedStamp(logo))
        assertStaysHeld(held)
    }

    @Test
    fun `cancelling during the retry wait stops the refresh and removes its staging dir`() = runTest {
        answer(MANIFEST_PATH, manifest())
        val refresh = async(Dispatchers.Default, start = CoroutineStart.DEFAULT) {
            delivery(retryDelayMillis = TimeUnit.MINUTES.toMillis(1)).refresh()
        }
        // The manifest, then the pack's first 404: the refresh now sits in the retry wait.
        assertNotNull(server.takeRequest(WAIT_SECONDS, TimeUnit.SECONDS))
        assertNotNull(server.takeRequest(WAIT_SECONDS, TimeUnit.SECONDS))

        refresh.cancelAndJoin()

        assertEquals(1, requested.count { it == LOGO_PACK_PATH })
        assertNull(store.tilePackFile(logo))
        assertFalse(File(deliveryRoot, "${logo.name}.tmp").exists())
    }

    // S0064: the wearer's Remove and Download. Remove must hold against the refresh the wearer triggers next.

    @Test
    fun `removing a set deletes its payload and its stamp together`() = runTest {
        installLogo()

        delivery().remove(logo)

        assertNull(store.tilePackFile(logo))
        assertNull("a stamp must never outlive its payload", store.installedStamp(logo))
        assertFalse(File(deliveryRoot, logo.name).exists())
        assertTrue(optOut.isOptedOut(logo))
    }

    @Test
    fun `removing a set that is not held records the removal and fails nothing`() = runTest {
        delivery().remove(preview)

        assertTrue(optOut.isOptedOut(preview))
        assertNull(store.tilePackFile(preview))
    }

    @Test
    fun `removing one set leaves the other held`() = runTest {
        val held = installLogo()

        delivery().remove(preview)

        assertStaysHeld(held)
        assertFalse(optOut.isOptedOut(logo))
    }

    @Test
    fun `a removed set is not fetched again by the refresh, even when the manifest differs`() = runTest {
        installLogo()
        val delivery = delivery()
        delivery.remove(logo)
        requested.clear()
        answer(MANIFEST_PATH, manifest(logoStamp = "logo-2"))
        answerLogoPayload(seed = 2)

        delivery.refresh()

        assertEquals(listOf(MANIFEST_PATH), requested.toList())
        assertNull(store.tilePackFile(logo))
        assertNull(store.installedStamp(logo))
    }

    @Test
    fun `when every set is removed the refresh does not ask the mirror at all`() = runTest {
        val delivery = delivery()
        delivery.remove(logo)
        delivery.remove(preview)
        answer(MANIFEST_PATH, manifest())

        delivery.refresh()

        assertEquals(emptyList<String>(), requested.toList())
    }

    @Test
    fun `download takes a removed set back and installs it`() = runTest {
        installLogo()
        val delivery = delivery()
        delivery.remove(logo)
        answer(MANIFEST_PATH, manifest())
        answerLogoPayload(seed = 2)

        val installed = delivery.download(logo)

        assertTrue(installed)
        assertNotNull(store.tilePackFile(logo))
        assertEquals("logo-1", store.installedStamp(logo))
        assertFalse(optOut.isOptedOut(logo))
    }

    @Test
    fun `after a download the refresh treats the set like any other again`() = runTest {
        val delivery = delivery()
        delivery.remove(logo)
        answer(MANIFEST_PATH, manifest(), manifest(logoStamp = "logo-2"))
        answerLogoPayload()
        assertTrue(delivery.download(logo))
        answerLogoPayload(seed = 2)

        delivery.refresh()

        assertEquals("logo-2", store.installedStamp(logo))
    }

    @Test
    fun `download of a set that is already held at the published stamp fetches nothing`() = runTest {
        installLogo()
        answer(MANIFEST_PATH, manifest())

        val installed = delivery().download(logo)

        assertTrue(installed)
        assertEquals(listOf(MANIFEST_PATH), requested.toList())
    }

    @Test
    fun `download with no answer from the mirror fails, and the set is no longer removed`() = runTest {
        val delivery = delivery()
        delivery.remove(logo)
        answer(MANIFEST_PATH, MockResponse().setResponseCode(404))

        val installed = delivery.download(logo)

        assertFalse(installed)
        assertNull(store.tilePackFile(logo))
        assertFalse("the wearer asked for it, so the next refresh may fetch it", optOut.isOptedOut(logo))
    }

    @Test
    fun `download of a set the manifest does not name fails`() = runTest {
        answer(MANIFEST_PATH, manifest())

        val installed = delivery().download(preview)

        assertFalse(installed)
        assertEquals(listOf(MANIFEST_PATH), requested.toList())
    }

    @Test
    fun `download whose payload fails verification fails and installs nothing`() = runTest {
        answer(MANIFEST_PATH, manifest())
        answer(LOGO_PACK_PATH, MockResponse().setBody("x".repeat(MIN_PACK_BYTES)))

        val installed = delivery().download(logo)

        assertFalse(installed)
        assertNull(store.tilePackFile(logo))
        assertFalse(File(deliveryRoot, "${logo.name}.tmp").exists())
    }

    @Test
    fun `a removal made while the set downloads leaves nothing installed`() = runTest {
        answer(MANIFEST_PATH, manifest())
        answer(LOGO_PACK_PATH, MockResponse().setBody(Buffer().write(packBytes())))
        // The sidecar answers late: the refresh has passed its early check and sits before the promotion.
        answer(LOGO_COORDS_PATH, MockResponse().setBody(coordsBody()).setHeadersDelay(LATE_ANSWER_SECONDS, TimeUnit.SECONDS))
        val delivery = delivery()
        val refresh = async(Dispatchers.Default, start = CoroutineStart.DEFAULT) { delivery.refresh() }
        repeat(3) { assertNotNull(server.takeRequest(WAIT_SECONDS, TimeUnit.SECONDS)) }

        delivery.remove(logo)
        refresh.await()

        assertNull(store.tilePackFile(logo))
        assertNull(store.installedStamp(logo))
        assertFalse(File(deliveryRoot, "${logo.name}.tmp").exists())
        assertTrue(optOut.isOptedOut(logo))
    }

    private companion object {
        const val LATE_ANSWER_SECONDS = 2L
        const val MANIFEST_PATH = "/artwork-manifest.json"
        const val LOGO_PACK_PATH = "/stream-logo-tiles.zip"
        const val LOGO_COORDS_PATH = "/stream-logo-coords.json"
        const val MIN_PACK_BYTES = 1_100_000
        const val COORDS_ROWS = 2_000
        const val MAX_ATTEMPTS = 4
        const val WAIT_SECONDS = 10L
    }
}
