package com.sza.streamsplayer.ui.player.helpers

import android.content.Context
import android.net.Uri
import android.util.Base64
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import com.sza.streamsplayer.BuildConfig
import com.sza.streamsplayer.domain.player.BroadcastPasswordRequiredException
import com.sza.streamsplayer.domain.player.BroadcastPlaybackPairs
import java.io.OutputStream

/**
 * Builds the streaming HTTP [DataSource.Factory] for internet stream playback.
 *
 * Cross-protocol redirects are mandatory for radio relays because many Icecast/Shoutcast stations
 * 30x-redirect across http<->https.
 *
 * By default suppress in-band ICY metadata, matching the donor. Media3 requests it for a progressive stream,
 * so the override must be applied at [DataSpec] priority inside the wrapper. The server then sends
 * uninterrupted audio bytes instead of alternating audio and metadata blocks.
 */
@UnstableApi
object StreamDataSourceFactoryProvider {

    /**
     * @param openSink when given, every byte read is also copied into the stream it returns.
     */
    fun create(context: Context, openSink: ((Uri) -> OutputStream?)? = null, requestIcyMetadata: Boolean = false): DataSource.Factory {
        val httpFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("StreamsPlayer/${BuildConfig.VERSION_NAME} (Android)")
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(CONNECT_TIMEOUT_MS)
            .setReadTimeoutMs(READ_TIMEOUT_MS)
            .setKeepPostFor302Redirects(false)
        val authAwareFactory = DataSource.Factory {
            RadioHttpDataSource(httpFactory.createDataSource(), requestIcyMetadata)
        }
        return DefaultDataSource.Factory(context, authAwareFactory)
    }

    private const val CONNECT_TIMEOUT_MS = 15_000
    private const val READ_TIMEOUT_MS = 15_000
}

/**
 * Many IP-camera / DVR HTTP endpoints are shared as `http://user:pass@host/path` and expect HTTP Basic Auth.
 * [java.net.HttpURLConnection] never reads credentials out of a URI's userinfo, so those streams silently 401.
 * This wrapper lifts a present userinfo into an explicit `Authorization: Basic` header and strips it from the URI.
 */
private class RadioHttpDataSource(
    private val delegate: HttpDataSource,
    private val requestIcyMetadata: Boolean,
) : DataSource by delegate {

    // Kotlin delegation omits Java default methods; Media3 needs these headers to strip ICY blocks.
    override fun getResponseHeaders(): Map<String, List<String>> = delegate.responseHeaders

    override fun open(dataSpec: DataSpec): Long {
        val uri = dataSpec.uri
        val userInfo = uri.userInfo
        val host = uri.host
        val authSpec = if (!userInfo.isNullOrEmpty() && host != null) {
            val authority = if (uri.port != -1) "$host:${uri.port}" else host
            dataSpec.withUri(uri.buildUpon().encodedAuthority(authority).build())
        } else {
            delegate.clearRequestProperty(HEADER_AUTHORIZATION)
            dataSpec
        }
        val headers = if (!userInfo.isNullOrEmpty() && host != null) {
            mapOf(
                HEADER_AUTHORIZATION to basicAuthHeaderValue(userInfo),
                HEADER_ICY_METADATA to if (requestIcyMetadata) "1" else ICY_DISABLED,
            )
        } else {
            mapOf(HEADER_ICY_METADATA to if (requestIcyMetadata) "1" else ICY_DISABLED)
        }
        val effectiveSpec = authSpec.withAdditionalHeaders(headers)
        return try {
            delegate.open(effectiveSpec)
        } catch (e: HttpDataSource.InvalidResponseCodeException) {
            if (e.responseCode != HTTP_UNAUTHORIZED) throw e
            reopenWithBroadcastPair(effectiveSpec, e)
        }
    }

    /**
     * S4258, LIVE-BROADCAST item M: a protected broadcast answers 401 with its challenge, answered once with the
     * pair bound to this address; without a pair, or with one it refuses, the player gets a typed refusal.
     */
    private fun reopenWithBroadcastPair(spec: DataSpec, refusal: HttpDataSource.InvalidResponseCodeException): Long {
        val url = spec.uri.toString()
        // A server may send one challenge per field, in any letter case; the answer reads the fields together.
        val challenge = refusal.headerFields.entries
            .filter { it.key.equals(HEADER_CHALLENGE, ignoreCase = true) }
            .flatMap { it.value }
            .takeIf { it.isNotEmpty() }
            ?.joinToString(", ")
        val pathAndQuery = spec.uri.encodedPath.orEmpty().ifEmpty { "/" } +
            spec.uri.encodedQuery?.let { "?$it" }.orEmpty()
        val answer = BroadcastPlaybackPairs.answerFor(url, challenge, METHOD_GET, pathAndQuery)
        if (answer == null) {
            throw if (BroadcastPlaybackPairs.isProtected(url, challenge)) {
                BroadcastPasswordRequiredException(refused = false)
            } else {
                refusal
            }
        }
        delegate.close()
        return try {
            delegate.open(spec.withAdditionalHeaders(mapOf(HEADER_AUTHORIZATION to answer)))
        } catch (e: HttpDataSource.InvalidResponseCodeException) {
            throw if (e.responseCode == HTTP_UNAUTHORIZED) BroadcastPasswordRequiredException(refused = true) else e
        }
    }

    private fun basicAuthHeaderValue(userInfo: String): String {
        val separatorIndex = userInfo.indexOf(':')
        val user = if (separatorIndex >= 0) userInfo.substring(0, separatorIndex) else userInfo
        val pass = if (separatorIndex >= 0) userInfo.substring(separatorIndex + 1) else ""
        val credentials = "${Uri.decode(user)}:${Uri.decode(pass)}"
        val encoded = Base64.encodeToString(credentials.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        return "Basic $encoded"
    }

    private companion object {
        const val HEADER_AUTHORIZATION = "Authorization"
        const val HEADER_ICY_METADATA = "Icy-MetaData"
        const val ICY_DISABLED = "0"
        const val HTTP_UNAUTHORIZED = 401
        const val HEADER_CHALLENGE = "WWW-Authenticate"
        const val METHOD_GET = "GET"
    }
}
