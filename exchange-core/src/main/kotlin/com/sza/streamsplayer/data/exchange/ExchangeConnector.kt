package com.sza.streamsplayer.data.exchange

import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.security.KeyStore
import java.security.MessageDigest
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/** Opens one stream to an exchange server - the control stream or one tunnel's data stream. */
interface ExchangeConnector {

    /**
     * Throws [ExchangeCertificateUntrustedException] or [ExchangeCertificateChangedException] when [trust]
     * refuses the certificate the server presented, and [IOException] when it cannot be reached.
     */
    fun connect(host: String, port: Int, trust: ExchangeTrust): ExchangeConnection =
        connect(host, port, (trust as? ExchangeTrust.Verified)?.pinned)

    /** Convenience overload preserving backward compatibility for callers passing pinnedCertificate directly. */
    fun connect(host: String, port: Int, pinnedCertificate: String?): ExchangeConnection =
        connect(host, port, ExchangeTrust.Verified(pinnedCertificate))
}

/** How the server's certificate is judged (contract DEVICE-EXCHANGE 7.1 with amendment D). */
sealed interface ExchangeTrust {

    /**
     * The platform trust store first; only when it refuses the chain does the leaf have to equal [pinned],
     * the fingerprint the user accepted or a connection string / descriptor stated (7.7, S0017).
     * A pin that differs is always refused.
     */
    data class Verified(val pinned: String?, val publicKey: String? = null) : ExchangeTrust
}

/** A connected stream and the fingerprint of the certificate the server presented on it. */
class ExchangeConnection(
    val socket: Socket,
    val certificateFingerprint: String,
    val publicKeyFingerprint: String? = null,
) : Closeable {
    val input: InputStream get() = socket.getInputStream()
    val output: OutputStream get() = socket.getOutputStream()

    override fun close() = socket.close()
}

/** The platform does not trust the server and nothing is pinned: the user must accept [presented] first. */
class ExchangeCertificateUntrustedException(val presented: String) :
    IOException("exchange server certificate is not trusted by the platform and not accepted")

/** The server presented [presentedFingerprint] where [pinnedFingerprint] was accepted; both are shown, never replaced silently. */
class ExchangeCertificateChangedException(
    val pinnedFingerprint: String? = null,
    val presentedFingerprint: String? = null,
) : IOException("exchange server certificate differs from the pinned one: pinned=$pinnedFingerprint presented=$presentedFingerprint") {
    val pinned: String get() = pinnedFingerprint.orEmpty()
    val presented: String get() = presentedFingerprint.orEmpty()
}

/** The decision of amendment D, apart from the socket so it can be judged without a TLS server. */
object ExchangeTrustDecision {

    enum class Verdict { ACCEPT, UNTRUSTED, CHANGED }

    fun decide(trust: ExchangeTrust, platformTrusted: Boolean, presented: String, presentedKey: String? = null): Verdict = when (trust) {
        is ExchangeTrust.Verified -> when {
            trust.pinned != null && trust.pinned != presented &&
                (trust.publicKey == null || trust.publicKey != presentedKey) -> Verdict.CHANGED
            trust.pinned != null || platformTrusted -> Verdict.ACCEPT
            else -> Verdict.UNTRUSTED
        }
    }
}

/**
 * TLS to the exchange server with the trust model of contract DEVICE-EXCHANGE amendment D: a CA-signed
 * deployment is trusted through the platform store and pinned nowhere, because its leaf renews on the CA's
 * schedule and a device holding no password could never re-enroll past a stale pin; a self-signed leaf is
 * trusted only by the fingerprint the user accepted.
 *
 * The handshake itself never fails on trust: the decision runs after it and before a single envelope is
 * written, so a refused server receives nothing but the handshake.
 */
@Singleton
class TlsExchangeConnector @Inject constructor() : ExchangeConnector {

    override fun connect(host: String, port: Int, trust: ExchangeTrust): ExchangeConnection {
        val inspector = InspectingTrustManager(platformTrustManager())
        val context = SSLContext.getInstance(TLS).apply { init(null, arrayOf(inspector), null) }
        val socket = context.socketFactory.createSocket() as SSLSocket
        try {
            socket.connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MS)
            socket.soTimeout = CONNECT_TIMEOUT_MS
            socket.enabledProtocols = socket.supportedProtocols.filter { it == "TLSv1.2" || it == "TLSv1.3" }.toTypedArray()
            socket.startHandshake()
            socket.soTimeout = 0
        } catch (e: IOException) {
            socket.close()
            throw e
        }
        val presented = inspector.presented
        if (presented == null) {
            socket.close()
            throw IOException("exchange server presented no certificate")
        }
        val platformTrusted = inspector.chainTrusted &&
            HttpsURLConnection.getDefaultHostnameVerifier().verify(host, socket.session)
        val verdict = ExchangeTrustDecision.decide(trust, platformTrusted, presented, inspector.presentedKey)
        if (verdict != ExchangeTrustDecision.Verdict.ACCEPT) socket.close()
        return when (verdict) {
            ExchangeTrustDecision.Verdict.ACCEPT -> ExchangeConnection(socket, presented, inspector.presentedKey)
            ExchangeTrustDecision.Verdict.UNTRUSTED -> throw ExchangeCertificateUntrustedException(presented)
            ExchangeTrustDecision.Verdict.CHANGED -> throw ExchangeCertificateChangedException(
                pinnedFingerprint = (trust as? ExchangeTrust.Verified)?.pinned.orEmpty(),
                presentedFingerprint = presented,
            )
        }
    }

    private fun platformTrustManager(): X509TrustManager? {
        val factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        factory.init(null as KeyStore?)
        return factory.trustManagers.filterIsInstance<X509TrustManager>().firstOrNull()
    }

    // Records what the platform thinks of the chain instead of failing the handshake on it.
    private class InspectingTrustManager(private val platform: X509TrustManager?) : X509TrustManager {
        @Volatile
        var presented: String? = null

        @Volatile
        var chainTrusted: Boolean = false

        @Volatile
        var presentedKey: String? = null

        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
            throw CertificateException("the exchange connector never accepts client connections")
        }

        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
            val leaf = chain?.firstOrNull() ?: throw CertificateException("server presented no certificate")
            presented = certificateFingerprint(leaf.encoded)
            presentedKey = certificateFingerprint(leaf.publicKey.encoded)
            // A self-signed or private-CA leaf fails here; the pin decides after the handshake.
            val platformCheck = platform ?: return
            chainTrusted = runCatching { platformCheck.checkServerTrusted(chain, authType) }.isSuccess
        }

        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }

    companion object {
        private const val TLS = "TLS"
        private const val CONNECT_TIMEOUT_MS = 15_000

        /** `SHA256:` plus the unpadded base64 digest of the leaf DER - the family's host-key text form. */
        fun certificateFingerprint(der: ByteArray): String =
            "SHA256:" + Base64.getEncoder().withoutPadding()
                .encodeToString(MessageDigest.getInstance("SHA-256").digest(der))
    }
}
