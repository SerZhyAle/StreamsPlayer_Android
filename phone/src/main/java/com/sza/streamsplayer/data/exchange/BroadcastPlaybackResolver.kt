package com.sza.streamsplayer.data.exchange

import com.sza.streamsplayer.data.broadcast.BroadcastDescriptorDto
import com.sza.streamsplayer.data.broadcast.BroadcastEndpointDto
import com.sza.streamsplayer.data.broadcast.auth.BroadcastCredentialPair
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.Closeable
import java.io.IOException
import java.net.URI
import javax.inject.Inject
import javax.inject.Singleton

/** DEVICE-EXCHANGE 7.7: ordered endpoints; returned proxies belong to the playback session. */
@Singleton
class BroadcastPlaybackResolver @Inject constructor(
    private val connector: ExchangeConnector,
    private val trustStore: ForeignEndpointTrustStore,
    private val passwords: com.sza.streamsplayer.domain.repository.BroadcastPasswordStore? = null,
    private val repository: com.sza.streamsplayer.data.repository.StreamSourceRepository? = null,
) {
    private val endpoints = BroadcastEndpointResolver(connector, trustStore)

    fun filterSupportedEndpoints(endpoints: List<BroadcastEndpointDto>) = this.endpoints.filterSupportedEndpoints(endpoints)
    fun parseFmsxUrl(raw: String) = endpoints.parseFmsxUrl(raw)

    suspend fun resolveNext(
        descriptor: BroadcastDescriptorDto,
        startIndex: Int = 0,
        password: String? = null,
        promptUserForCert: suspend (presentedFingerprint: String, hostPort: String) -> Boolean = { _, _ -> false },
    ): BroadcastEndpointResolver.ResolvedPlayback? {
        if (descriptor.auth?.isPasswordV1 == false) throw UnsupportedBroadcastAuthException()
        // DEVICE-EXCHANGE item BF: an own broadcast's opened record carries the account pair, which answers with no
        // question; a kept channel answers with the pair its row keeps, the own one included.
        val own = descriptor.credentials
        val stored = repository?.getByUrl(descriptor.url)?.let { passwords?.get(it.id) }
        val pair = descriptor.auth?.let { auth ->
            when {
                password != null -> BroadcastCredentialPair(auth.user, password)
                own != null -> BroadcastCredentialPair(own.user, own.password)
                stored?.password != null -> BroadcastCredentialPair(stored.user, stored.password)
                else -> throw com.sza.streamsplayer.domain.player.BroadcastPasswordRequiredException(false)
            }
        }
        val resolved = endpoints.resolveNext(descriptor, startIndex, promptUserForCert) ?: return null
        com.sza.streamsplayer.domain.player.BroadcastPlaybackPairs.bind(resolved.playUrl, pair)
        return resolved.copy(closeableProxy = Closeable {
            resolved.closeableProxy?.close()
            com.sza.streamsplayer.domain.player.BroadcastPlaybackPairs.bind(resolved.playUrl, null)
        })
    }
}

class UnsupportedBroadcastAuthException : java.io.IOException("Broadcast authentication scheme is unsupported")
