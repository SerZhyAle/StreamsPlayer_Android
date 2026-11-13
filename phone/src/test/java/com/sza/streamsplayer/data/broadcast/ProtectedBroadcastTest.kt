package com.sza.streamsplayer.data.broadcast

import com.sza.streamsplayer.data.broadcast.auth.BroadcastCredentialPair
import com.sza.streamsplayer.data.exchange.BroadcastPlaybackResolver
import com.sza.streamsplayer.data.exchange.ExchangeConnector
import com.sza.streamsplayer.data.exchange.ExchangeTrust
import com.sza.streamsplayer.data.exchange.ForeignEndpointTrustStore
import com.sza.streamsplayer.data.exchange.UnsupportedBroadcastAuthException
import com.sza.streamsplayer.domain.player.BroadcastPasswordRequiredException
import com.sza.streamsplayer.domain.player.BroadcastPlaybackPairs
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import timber.log.Timber

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ProtectedBroadcastTest {
    private val parser = BroadcastDescriptorParser()
    private val serializer = BroadcastDescriptorSerializer()
    private val descriptor = BroadcastDescriptorDto(url = "http://phone:8768/live", mode = "AUDIO_ONLY",
        auth = BroadcastAuthDto(user = "listener"))

    @Test
    fun `auth survives the file compressed link and descriptor store round trip`() {
        val json = serializer.serialize(descriptor)
        assertFalse(json.contains("password\""))
        assertEquals(descriptor.auth, parser.parse(json)?.auth)
        assertEquals(descriptor.auth, parser.parse("fmsbcast://import?payload=" +
            java.net.URLEncoder.encode(serializer.serializeCompressed(descriptor), "UTF-8"))?.auth)
        val store = BroadcastDescriptorStore(RuntimeEnvironment.getApplication())
        store.saveDescriptor(descriptor.url, null, descriptor)
        assertEquals(descriptor.auth, store.getByUrl(descriptor.url)?.auth)
    }

    @Test
    fun `malformed auth is refused while an unknown scheme is retained for explicit denial`() {
        for (auth in listOf("{}", "5", "{\"scheme\":\"password-v1\"}",
            "{\"scheme\":\"password-v1\",\"user\":5}")) {
            assertEquals(BroadcastDescriptorParser.ParseOutcome.Malformed,
                parser.parseDetailed("""{"url":"http://phone/live","mode":"AUDIO_ONLY","auth":$auth}"""))
        }
        assertEquals("future", parser.parse(serializer.serialize(descriptor.copy(
            auth = BroadcastAuthDto("future", "listener"))))?.auth?.scheme)
    }

    @Test
    fun `a password is required before any endpoint and unknown protection never connects`() = runBlocking {
        var connects = 0
        val resolver = BroadcastPlaybackResolver(object : ExchangeConnector {
            override fun connect(host: String, port: Int, trust: ExchangeTrust): com.sza.streamsplayer.data.exchange.ExchangeConnection {
                connects++
                error("A protected endpoint must wait for the password")
            }
        }, ForeignEndpointTrustStore(RuntimeEnvironment.getApplication()))
        try { resolver.resolveNext(descriptor); fail("Password question expected") }
        catch (_: BroadcastPasswordRequiredException) { }
        try { resolver.resolveNext(descriptor.copy(auth = BroadcastAuthDto("future", "listener"))); fail("Explicit denial expected") }
        catch (_: UnsupportedBroadcastAuthException) { }
        assertEquals(0, connects)
    }

    @Test
    fun `temporary playback keeps its password out of logs URLs and persistent state and releases it`() = runBlocking {
        val messages = mutableListOf<String>()
        val tree = object : Timber.Tree() {
            override fun log(priority: Int, tag: String?, message: String, t: Throwable?) { messages.add(message) }
        }
        Timber.plant(tree)
        try {
            val resolver = BroadcastPlaybackResolver(object : ExchangeConnector {},
                ForeignEndpointTrustStore(RuntimeEnvironment.getApplication()))
            val resolved = checkNotNull(resolver.resolveNext(descriptor, password = "canary-secret-0028"))
            assertEquals(descriptor.url, resolved.playUrl)
            assertEquals("canary-secret-0028", BroadcastPlaybackPairs.forUrl(resolved.playUrl)?.password)
            assertFalse(serializer.serialize(descriptor).contains("canary-secret-0028"))
            assertFalse(messages.any { it.contains("canary-secret-0028") })
            resolved.closeableProxy?.close()
            assertNull(BroadcastPlaybackPairs.forUrl(resolved.playUrl))
        } finally { Timber.uproot(tree) }
    }

    @Test
    fun `an own broadcast plays with its opened record's pair, which is kept in memory only`() = runBlocking {
        val own = descriptor.copy(credentials = BroadcastCredentialsDto(user = "account", password = "own-canary-0028"))
        val store = BroadcastDescriptorStore(RuntimeEnvironment.getApplication())
        store.saveDescriptor(own.url, null, own)
        val prefs = RuntimeEnvironment.getApplication()
            .getSharedPreferences("broadcast_descriptors", android.content.Context.MODE_PRIVATE)
        assertFalse(prefs.all.values.any { it.toString().contains("own-canary-0028") })
        val reread = checkNotNull(store.getByUrl(own.url))
        assertEquals("own-canary-0028", reread.credentials?.password)
        assertFalse(reread.toString().contains("own-canary-0028"))

        val resolver = BroadcastPlaybackResolver(object : ExchangeConnector {},
            ForeignEndpointTrustStore(RuntimeEnvironment.getApplication()))
        val resolved = checkNotNull(resolver.resolveNext(reread))
        assertEquals(BroadcastCredentialPair("account", "own-canary-0028"), BroadcastPlaybackPairs.forUrl(resolved.playUrl))
        resolved.closeableProxy?.close()
        store.remove(own.url)
        assertNull(store.getByUrl(own.url))
    }
}
