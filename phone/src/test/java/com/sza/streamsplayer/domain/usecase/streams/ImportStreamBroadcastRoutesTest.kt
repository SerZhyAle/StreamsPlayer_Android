package com.sza.streamsplayer.domain.usecase.streams

import com.sza.streamsplayer.data.broadcast.BroadcastDescriptorDto
import com.sza.streamsplayer.data.broadcast.BroadcastDescriptorParser
import com.sza.streamsplayer.data.broadcast.BroadcastDescriptorSerializer
import com.sza.streamsplayer.data.broadcast.BroadcastDescriptorStore
import com.sza.streamsplayer.data.local.db.StreamSourceEntity
import com.sza.streamsplayer.data.repository.StreamSourceRepository
import com.sza.streamsplayer.testing.InMemoryRoomRule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ImportStreamBroadcastRoutesTest {

    @get:Rule
    val dbRule = InMemoryRoomRule { RuntimeEnvironment.getApplication() }

    private val context get() = RuntimeEnvironment.getApplication()
    private val dao get() = dbRule.db.streamSourceDao()
    private val repo get() = StreamSourceRepository(
        dbRule.db,
        dao,
        dbRule.db.streamQualityMemoryDao(),
        dbRule.db.streamUserStateDao(),
        CoroutineScope(Dispatchers.Unconfined),
    )
    private val parser = BroadcastDescriptorParser()
    private val serializer = BroadcastDescriptorSerializer()
    private val descriptorStore get() = BroadcastDescriptorStore(context)
    private val addStreamUseCase get() = AddStreamSourceUseCase(repo, StreamMediaKindClassifier())
    private val importUseCase get() = ImportStreamBroadcastUseCase(parser, addStreamUseCase, repo, descriptorStore)

    @Test
    fun `import from fmsbcast link extracts compressed payload and stores descriptor`() = runTest {
        val dto = BroadcastDescriptorDto(
            schemaVersion = 1,
            url = "http://192.168.1.50:8768/live-audio.aac",
            title = "Import Link Broadcast",
            mode = "AUDIO_ONLY",
            sourceId = "link-source-123",
        )
        val compressed = serializer.serializeCompressed(dto)
        val link = "fmsbcast://import?payload=$compressed"

        val result = importUseCase(link)
        assertEquals(ImportStreamBroadcastUseCase.ImportResult.Success, result)

        val entity = dao.getByUrl("http://192.168.1.50:8768/live-audio.aac")
        assertNotNull(entity)
        assertEquals("Import Link Broadcast", entity?.title)

        val stored = descriptorStore.getByUrl("http://192.168.1.50:8768/live-audio.aac")
        assertNotNull(stored)
        assertEquals("link-source-123", stored?.sourceId)
    }

    @Test
    fun `import from intent link extracts payload and stores descriptor`() = runTest {
        val dto = BroadcastDescriptorDto(
            schemaVersion = 1,
            url = "http://192.168.1.50:8768/live-audio.aac",
            title = "Intent Broadcast",
            mode = "AUDIO_ONLY",
            sourceId = "intent-src-456",
        )
        val compressed = serializer.serializeCompressed(dto)
        val intentUri = "intent://import?payload=$compressed#Intent;scheme=fmsbcast;end"

        val result = importUseCase(intentUri)
        assertEquals(ImportStreamBroadcastUseCase.ImportResult.Success, result)

        val stored = descriptorStore.getBySourceId("intent-src-456")
        assertNotNull(stored)
        assertEquals("http://192.168.1.50:8768/live-audio.aac", stored?.url)
    }

    @Test
    fun `import from https fragment link extracts payload and stores descriptor`() = runTest {
        val dto = BroadcastDescriptorDto(
            schemaVersion = 1,
            url = "http://192.168.1.50:8768/live-audio.aac",
            title = "Web Fragment Broadcast",
            mode = "AUDIO_ONLY",
            sourceId = "frag-src-789",
        )
        val compressed = serializer.serializeCompressed(dto)
        val httpsLink = "https://example.com/share#payload=$compressed"

        val result = importUseCase(httpsLink)
        assertEquals(ImportStreamBroadcastUseCase.ImportResult.Success, result)

        val stored = descriptorStore.getBySourceId("frag-src-789")
        assertNotNull(stored)
    }

    @Test
    fun `import raw JSON payload succeeds and saves descriptor`() = runTest {
        val dto = BroadcastDescriptorDto(
            schemaVersion = 1,
            url = "rtsp://192.168.1.80:8554/live.sdp",
            title = "JSON Rtsp Broadcast",
            mode = "VIDEO_AUDIO",
            sourceId = "json-src-321",
        )
        val json = serializer.serialize(dto)

        val result = importUseCase(json)
        assertEquals(ImportStreamBroadcastUseCase.ImportResult.Success, result)

        val stored = descriptorStore.getByUrl("rtsp://192.168.1.80:8554/live.sdp")
        assertNotNull(stored)
        assertEquals("VIDEO_AUDIO", stored?.mode)
    }

    @Test
    fun `malformed payload returns InvalidDescriptor`() = runTest {
        val result = importUseCase("fmsbcast://import?payload=INVALID_GARBAGE")
        assertEquals(ImportStreamBroadcastUseCase.ImportResult.InvalidDescriptor, result)
    }

    @Test
    fun `unsupported schema version returns UnsupportedVersion`() = runTest {
        val dto = BroadcastDescriptorDto(
            schemaVersion = 999,
            url = "http://192.168.1.50:8768/live-audio.aac",
            title = "Future",
            mode = "AUDIO_ONLY",
        )
        val json = serializer.serialize(dto)

        val result = importUseCase(json)
        assertEquals(ImportStreamBroadcastUseCase.ImportResult.UnsupportedVersion, result)
    }

    @Test
    fun `tunnel-only audio descriptor imports without falling into plain stream routing`() = runTest {
        val url = "fmsx://exchange.example/b/capability/http"
        val dto = BroadcastDescriptorDto(url = url, mode = "AUDIO_ONLY", sourceId = "tunnel-source",
            endpoints = listOf(com.sza.streamsplayer.data.broadcast.BroadcastEndpointDto(
                url = url, transport = "TUNNEL", mode = "AUDIO_ONLY", inner = "http://127.0.0.1/live.aac")))
        assertEquals(ImportStreamBroadcastUseCase.ImportResult.Success, importUseCase(serializer.serialize(dto)))
        assertEquals("AUDIO", dao.getByUrl(url)?.mediaKind)
        assertNotNull(descriptorStore.getByUrl(url))
    }

    @Test
    fun `malformed null fields never import or persist a partial descriptor`() = runTest {
        val malformed = listOf("""{"schemaVersion":1,"mode":"AUDIO_ONLY"}""",
            """{"schemaVersion":1,"url":"http://host/live","mode":null}""",
            """{"schemaVersion":1,"url":"http://host/live","mode":"AUDIO_ONLY","endpoints":[null]}""")
        for (text in malformed) {
            assertEquals(ImportStreamBroadcastUseCase.ImportResult.InvalidDescriptor, importUseCase(text))
        }
        assertEquals(null, dao.getByUrl("http://host/live"))
        assertEquals(null, descriptorStore.getByUrl("http://host/live"))
    }
}
