package com.sza.streamsplayer.data.exchange.drive

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import timber.log.Timber

/**
 * The REST client over a local server: every call stays in the app folder under the one scope, a 401 is answered
 * once with a fresh token, Drive's own clock is read from its `Date`, and no token, file id, file name or
 * file body reaches a log.
 */
class HttpDriveFilesApiTest {

    private lateinit var server: MockWebServer
    private val tokens = CanaryTokenSource()
    private val logs = mutableListOf<String>()
    private val tree = object : Timber.Tree() {
        override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
            logs += message
            t?.message?.let { logs += it }
        }
    }

    private lateinit var api: HttpDriveFilesApi

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        api = HttpDriveFilesApi(OkHttpClient(), tokens, server.url("/").toString().trimEnd('/'))
        Timber.plant(tree)
    }

    @After
    fun tearDown() {
        Timber.uproot(tree)
        server.shutdown()
    }

    private fun json(body: String, code: Int = 200) = MockResponse().setResponseCode(code).setBody(body)
        .setHeader("Date", "Fri, 23 Oct 2026 13:49:25 GMT")

    @Test
    fun `list asks for the app folder with the bearer token, keeps only the prefix and reads Drive's clock`() = runBlocking {
        server.enqueue(
            json(
                """{"files":[
                    {"id":"ID-ONE-CANARY","name":"fmsx-broadcast-A.json","modifiedTime":"2026-10-23T13:47:32.267Z"},
                    {"id":"ID-TWO","name":"cross_device_transfer","modifiedTime":"2026-10-23T13:00:00.000Z"},
                    {"id":"ID-THREE","name":"fmsx-device-B.json","modifiedTime":"2026-10-23T13:48:00Z"}]}"""
            )
        )

        val listing = api.list("fmsx-")

        assertEquals(listOf("fmsx-broadcast-A.json", "fmsx-device-B.json"), listing.files.map { it.name })
        assertEquals(1_792_763_252_267L, listing.files.first().modifiedTimeMs)
        assertEquals(1_792_763_365_000L, listing.driveNowMs)
        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("Bearer ${CanaryTokenSource.TOKEN}", request.getHeader("Authorization"))
        assertEquals("appDataFolder", request.requestUrl?.queryParameter("spaces"))
    }

    @Test
    fun `create posts to the app folder only`() = runBlocking {
        server.enqueue(json("""{"id":"NEW-ID-CANARY"}"""))

        val id = api.create("fmsx-device-X.json", """{"schemaVersion":1}""")

        assertEquals("NEW-ID-CANARY", id)
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("multipart", request.requestUrl?.queryParameter("uploadType"))
        assertTrue(request.getHeader("Content-Type").orEmpty().startsWith("multipart/related"))
        val body = request.body.readUtf8()
        assertTrue(body.contains(""""parents":["appDataFolder"]"""))
        assertTrue(body.contains(""""name":"fmsx-device-X.json""""))
        assertTrue(body.contains("""{"schemaVersion":1}"""))
    }

    @Test
    fun `update rewrites the content of the file by its id`() = runBlocking {
        server.enqueue(json("""{"id":"F1"}"""))

        api.update("F1", """{"a":1}""")

        val request = server.takeRequest()
        assertEquals("PATCH", request.method)
        assertEquals("/upload/drive/v3/files/F1", request.requestUrl?.encodedPath)
        assertEquals("media", request.requestUrl?.queryParameter("uploadType"))
        assertEquals("""{"a":1}""", request.body.readUtf8())
    }

    @Test
    fun `read returns the text and a missing file is null, not an error`() = runBlocking {
        server.enqueue(json("""{"text":"body"}"""))
        server.enqueue(json("""{"error":{"code":404,"errors":[{"reason":"notFound"}]}}""", 404))

        assertEquals("""{"text":"body"}""", api.read("F1"))
        assertNull(api.read("GONE"))
        assertEquals("media", server.takeRequest().requestUrl?.queryParameter("alt"))
    }

    @Test
    fun `delete of a file that is already gone is not an error`() = runBlocking {
        server.enqueue(json("""{"error":{"code":404}}""", 404))
        api.delete("GONE")
        assertEquals("DELETE", server.takeRequest().method)
    }

    @Test
    fun `a refused token is replaced once and the call is repeated`() = runBlocking {
        server.enqueue(json("""{"error":{"code":401}}""", 401))
        server.enqueue(json("""{"files":[]}"""))

        api.list("fmsx-")

        assertEquals(listOf(CanaryTokenSource.TOKEN), tokens.invalidated)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `a second refusal is final`() = runBlocking {
        server.enqueue(json("""{"error":{"code":401}}""", 401))
        server.enqueue(json("""{"error":{"code":401}}""", 401))

        try {
            api.list("fmsx-")
            fail("expected Unauthorized")
        } catch (failure: DriveException.Unauthorized) {
            assertTrue(failure.final)
        }
    }

    @Test
    fun `Drive's refusals map to what the channel acts on`() = runBlocking {
        fun refuse(code: Int, reason: String) =
            server.enqueue(json("""{"error":{"code":$code,"errors":[{"reason":"$reason"}]}}""", code))

        suspend fun failureOf(): DriveException = try {
            api.delete("F")
            error("no failure")
        } catch (failure: DriveException) {
            failure
        }

        refuse(403, "rateLimitExceeded")
        assertTrue(failureOf() is DriveException.RateLimited)
        refuse(429, "tooManyRequests")
        assertTrue(failureOf() is DriveException.RateLimited)
        refuse(403, "storageQuotaExceeded")
        assertTrue(failureOf().let { it is DriveException.DriveFull && it.final })
        refuse(403, "dailyLimitExceeded")
        assertTrue(failureOf() is DriveException.DailyLimit)
        refuse(403, "insufficientPermissions")
        assertTrue(failureOf() is DriveException.Forbidden)
        refuse(503, "backendError")
        assertTrue(failureOf().let { it is DriveException.Server && !it.final })
    }

    @Test
    fun `no token, file id, file name or file body reaches a log`() = runBlocking {
        server.enqueue(json("""{"files":[{"id":"ID-ONE-CANARY","name":"fmsx-broadcast-NAME-CANARY.json","modifiedTime":"2026-10-23T13:47:32Z"}]}"""))
        server.enqueue(json("BODY-CANARY"))
        server.enqueue(json("""{"id":"NEW-ID-CANARY"}"""))
        server.enqueue(json("""{"id":"ID-ONE-CANARY"}"""))
        server.enqueue(json("""{"error":{"code":403,"message":"mail user@example.com","errors":[{"reason":"rateLimitExceeded"}]}}""", 403))

        api.list("fmsx-")
        api.read("ID-ONE-CANARY")
        api.create("fmsx-broadcast-NAME-CANARY.json", "BODY-CANARY")
        api.update("ID-ONE-CANARY", "BODY-CANARY")
        runCatching { api.delete("ID-ONE-CANARY") }

        assertTrue(logs.isNotEmpty())
        val all = logs.joinToString("\n")
        listOf(CanaryTokenSource.TOKEN, "CANARY-ACCESS", "ID-ONE-CANARY", "NEW-ID-CANARY", "NAME-CANARY", "BODY-CANARY", "user@example.com", "@")
            .forEach { assertFalse("log leaks $it:\n$all", all.contains(it)) }
    }

    @Test
    fun `an unreachable Drive is a network failure that a retry may help with`() = runBlocking {
        val dead = HttpDriveFilesApi(OkHttpClient(), tokens, "http://127.0.0.1:1")
        try {
            dead.list("fmsx-")
            fail("expected Network")
        } catch (failure: DriveException.Network) {
            assertFalse(failure.final)
        }
    }
}
