package com.sza.streamsplayer.data.exchange.session

import com.google.gson.JsonArray
import com.sza.streamsplayer.data.exchange.DeviceExchangeAccountEntry
import com.sza.streamsplayer.data.exchange.DeviceExchangeEnvelope
import com.sza.streamsplayer.data.exchange.DeviceExchangeFrameCodec
import com.sza.streamsplayer.data.exchange.DeviceExchangeWireException
import com.sza.streamsplayer.data.exchange.ExchangeCertificateChangedException
import com.sza.streamsplayer.data.exchange.ExchangeCertificateUntrustedException
import com.sza.streamsplayer.data.exchange.ExchangeConnector
import com.sza.streamsplayer.data.exchange.ExchangeTrust
import com.sza.streamsplayer.data.exchange.SecretStore
import com.sza.streamsplayer.data.security.e2e.E2eDeviceKey
import com.sza.streamsplayer.data.exchange.ExchangeLog as Timber
import java.io.IOException
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Account client of contract DEVICE-EXCHANGE sections 5 and 7.2-7.4:
 * enrollment and opening authenticated sessions with `hello`.
 */
@Singleton
class DeviceExchangeAccountClient @Inject constructor(
    private val connector: ExchangeConnector,
    private val tokens: SecretStore,
    private val entries: com.sza.streamsplayer.data.exchange.EntryStore? = null,
    private val product: ExchangeProductVersion = ExchangeProductVersion.UNKNOWN,
    private val e2eKeys: DeviceExchangeE2eKeyStore? = null,
) {

    /**
     * Enrolls [entry] with [credential] and stores the received device token in [tokens].
     * Password [credential] is wiped immediately after the enrollment request is sent.
     */
    suspend fun enroll(
        entry: DeviceExchangeAccountEntry,
        credential: DeviceExchangeCredential,
        enrollment: DeviceExchangeEnrollment,
        keepaliveSeconds: Int? = null,
    ): DeviceExchangeEnrollResult {
        val sanitizedDeviceId = hashId(entry.deviceId)
        Timber.d("S0016: enrolling deviceId=$sanitizedDeviceId host=${entry.host}:${entry.port}")
        val e2eKey = e2eKeys?.keyFor(entry.deviceId)
        return try {
            val answer = connectEntry(entry).use { stream ->
                stream.socket.soTimeout = 15_000
                try {
                    val envelope = enrollEnvelope(entry, credential, enrollment, keepaliveSeconds, e2eKey)
                    DeviceExchangeFrameCodec.write(stream.output, envelope, DeviceExchangeFrameCodec.MAX_FRAME_BODY)
                } finally {
                    (credential as? DeviceExchangeCredential.Password)?.wipe()
                }
                DeviceExchangeFrameCodec.read(stream.input, DeviceExchangeFrameCodec.MAX_FRAME_BODY).also { resp ->
                    if (resp?.type == DeviceExchangeEnvelope.TYPE_ENROLLED) {
                        DeviceExchangeFrameCodec.write(
                            stream.output,
                            DeviceExchangeEnvelope.of(DeviceExchangeEnvelope.TYPE_BYE),
                            DeviceExchangeFrameCodec.MAX_FRAME_BODY,
                        )
                    }
                }
            }
            enrollAnswer(entry, answer)
        } catch (e: IOException) {
            connectFailure(e)
        } finally {
            (credential as? DeviceExchangeCredential.Password)?.wipe()
        }
    }

    /**
     * Opens a stream authenticated by `hello`.
     * A `device-revoked` refusal forgets the token from [tokens].
     *
     * @throws DeviceExchangeNotEnrolledException when [entry] holds no token.
     * @throws DeviceExchangeRefusedException when the server refused the token.
     * @throws IOException when the server cannot be reached or its certificate is refused.
     */
    suspend fun openAuthenticated(
        entry: DeviceExchangeAccountEntry,
        keepaliveSeconds: Int? = null,
    ): DeviceExchangeSession {
        val token = tokens.token(entry) ?: throw DeviceExchangeNotEnrolledException()
        val sanitizedDeviceId = hashId(entry.deviceId)
        Timber.d("S0016: opening authenticated stream deviceId=$sanitizedDeviceId host=${entry.host}:${entry.port}")
        val connection = connectEntry(entry)
        connection.socket.soTimeout = 15_000
        val hello = helloEnvelope(entry, token, keepaliveSeconds)
        val answer = try {
            DeviceExchangeFrameCodec.write(connection.output, hello, DeviceExchangeFrameCodec.MAX_FRAME_BODY)
            DeviceExchangeFrameCodec.read(connection.input, DeviceExchangeFrameCodec.MAX_FRAME_BODY)
        } catch (e: IOException) {
            connection.close()
            throw e
        }

        if (answer?.type == DeviceExchangeEnvelope.TYPE_WELCOME) {
            connection.socket.soTimeout = 0
            Timber.d("S0016: authenticated stream established deviceId=$sanitizedDeviceId")
            val welcome = DeviceExchangeWelcome.from(answer)
            try { recordWelcome(entry, welcome) }
            catch (failure: Exception) { connection.close(); throw failure }
            return DeviceExchangeSession(connection, welcome)
        }
        connection.close()
        throw helloFailure(entry, answer)
    }

    private suspend fun helloFailure(entry: DeviceExchangeAccountEntry, answer: DeviceExchangeEnvelope?): IOException {
        val reason = answer?.takeIf { it.type == DeviceExchangeEnvelope.TYPE_REFUSED }
            ?.string(DeviceExchangeEnvelope.KEY_REASON)
        if (reason in AUTHENTICATION_REASONS) {
            Timber.d("S0016: device revoked; forgetting token for deviceId=${hashId(entry.deviceId)}")
            tokens.forget(entry)
        }
        if (reason in AUTHENTICATION_REASONS) {
            entries?.getAccountEntry()?.takeIf { it.deviceId == entry.deviceId }?.let {
                entries.saveAccountEntry(it.copy(
                    state = com.sza.streamsplayer.data.exchange.ExchangeAccountState.NeedsSignIn,
                    signInReason = reason,
                ))
            }
        }
        return reason?.let(::DeviceExchangeRefusedException)
            ?: DeviceExchangeWireException("hello was not answered with welcome")
    }

    private suspend fun enrollAnswer(
        entry: DeviceExchangeAccountEntry,
        answer: DeviceExchangeEnvelope?,
    ): DeviceExchangeEnrollResult {
        val token = answer?.takeIf { it.type == DeviceExchangeEnvelope.TYPE_ENROLLED }?.string(KEY_DEVICE_TOKEN)
        val reason = answer?.takeIf { it.type == DeviceExchangeEnvelope.TYPE_REFUSED }
            ?.let { it.string(DeviceExchangeEnvelope.KEY_REASON) ?: REASON_UNKNOWN }
        return when {
            token != null -> if (tokens.save(entry, token)) {
                Timber.d("S0016: enrolled successfully deviceId=${hashId(entry.deviceId)}")
                val welcome = DeviceExchangeWelcome.from(answer)
                recordWelcome(entry, welcome)
                DeviceExchangeEnrollResult.Enrolled(welcome)
            } else {
                Timber.d("S0016: failed to save token for deviceId=${hashId(entry.deviceId)}")
                DeviceExchangeEnrollResult.Refused(DeviceExchangeEnrollResult.REASON_TOKEN_NOT_STORED)
            }
            reason != null -> {
                Timber.d("S0016: enrollment refused: $reason")
                DeviceExchangeEnrollResult.Refused(reason)
            }
            else -> DeviceExchangeEnrollResult.Unreachable(DeviceExchangeWireException("enroll was not answered"))
        }
    }

    private fun connectFailure(failure: IOException): DeviceExchangeEnrollResult = when (failure) {
        is ExchangeCertificateUntrustedException -> {
            Timber.d("S0016: certificate untrusted presented=${failure.presented}")
            DeviceExchangeEnrollResult.CertificateUntrusted(failure.presented)
        }
        is ExchangeCertificateChangedException -> {
            Timber.d("S0016: certificate changed pinned=${failure.pinned} presented=${failure.presented}")
            DeviceExchangeEnrollResult.CertificateChanged(failure.pinned, failure.presented)
        }
        else -> {
            Timber.d(failure, "S0016: server unreachable")
            DeviceExchangeEnrollResult.Unreachable(failure)
        }
    }

    private suspend fun connectEntry(entry: DeviceExchangeAccountEntry): com.sza.streamsplayer.data.exchange.ExchangeConnection {
        val saved = entries?.getAccountEntry()?.takeIf { it.deviceId == entry.deviceId }
        val pin = saved?.pin ?: entry.pinnedCertificate
        val key = saved?.pinnedPublicKey ?: entry.pinnedPublicKey
        val connection = connector.connect(entry.host, entry.port, ExchangeTrust.Verified(pin, key))
        if (pin != null && saved != null) {
            try {
                check(entries?.saveAccountEntry(saved.copy(
                    pin = connection.certificateFingerprint,
                    pinnedPublicKey = connection.publicKeyFingerprint ?: key,
                )) == true)
            } catch (failure: Exception) {
                connection.close()
                throw failure
            }
        }
        return connection
    }

    private suspend fun recordWelcome(entry: DeviceExchangeAccountEntry, welcome: DeviceExchangeWelcome) {
        entries?.getAccountEntry()?.takeIf { it.deviceId == entry.deviceId }?.let {
            entries.saveAccountEntry(it.copy(accountKind = welcome.accountKind,
                state = com.sza.streamsplayer.data.exchange.ExchangeAccountState.Connected, signInReason = null))
        }
    }

    /** Removal is a flushed write; DEVICE-EXCHANGE 7.4 defines no reply to revoke. */
    suspend fun revoke(entry: DeviceExchangeAccountEntry, deviceId: String = entry.deviceId) {
        openAuthenticated(entry).use { session ->
            session.send(DeviceExchangeEnvelope.of(DeviceExchangeEnvelope.TYPE_REVOKE) {
                addProperty(KEY_DEVICE_ID, deviceId)
            })
        }
    }

    // Ported from FastMediaSorter Android's account client, DEVICE-EXCHANGE 19 item AT.
    suspend fun changePassword(
        entry: DeviceExchangeAccountEntry,
        oldPassword: DeviceExchangeCredential.Password,
        newPassword: DeviceExchangeCredential.Password,
    ) {
        try {
            val token = openAuthenticated(entry).use { session ->
                session.connection.socket.soTimeout = 15_000
                try {
                    session.send(DeviceExchangeEnvelope.of("password-change") {
                        addProperty("oldPassword", oldPassword.value)
                        addProperty("newPassword", newPassword.value)
                    })
                } finally {
                    oldPassword.wipe()
                    newPassword.wipe()
                }
                var answer = session.receive()
                var skipped = 0
                while (answer != null && answer.type !in listOf("password-changed", "refused") && skipped++ < 32) {
                    answer = session.receive()
                }
                if (answer?.type == "refused") throw DeviceExchangeRefusedException(
                    answer.string(DeviceExchangeEnvelope.KEY_REASON) ?: REASON_UNKNOWN)
                answer?.takeIf { it.type == "password-changed" }?.string(KEY_DEVICE_TOKEN)
                    ?: throw DeviceExchangeWireException("password-change was not answered with password-changed")
            }
            if (!tokens.save(entry, token)) {
                tokens.forget(entry)
                entries?.updateState(com.sza.streamsplayer.data.exchange.ExchangeAccountState.NeedsSignIn)
                throw DeviceExchangeRefusedException(DeviceExchangeEnrollResult.REASON_TOKEN_NOT_STORED)
            }
        } finally {
            oldPassword.wipe()
            newPassword.wipe()
        }
    }

    private fun enrollEnvelope(
        entry: DeviceExchangeAccountEntry,
        credential: DeviceExchangeCredential,
        enrollment: DeviceExchangeEnrollment,
        keepaliveSeconds: Int?,
        e2eKey: E2eDeviceKey? = null,
    ) = DeviceExchangeEnvelope.of(DeviceExchangeEnvelope.TYPE_ENROLL) {
        addProperty(KEY_LOGIN, entry.login)
        when (credential) {
            is DeviceExchangeCredential.Password -> addProperty(KEY_PASSWORD, credential.value)
            is DeviceExchangeCredential.PairingCode -> addProperty(KEY_PAIRING_CODE, credential.value)
        }
        addProperty(KEY_DEVICE_ID, entry.deviceId)
        addProperty(KEY_DEVICE_NAME, enrollment.deviceName)
        addProperty(KEY_PRODUCT, enrollment.product)
        addProperty(KEY_PRODUCT_VERSION, enrollment.productVersion)
        addProperty(KEY_PLATFORM, enrollment.platform)
        add(KEY_ROLES, JsonArray().apply { enrollment.roles.forEach(::add) })
        enrollment.receiver?.let { add(KEY_RECEIVER, it) }
        val capabilities = enrollment.capabilities + listOfNotNull(e2eKey?.let { E2eDeviceKey.CAPABILITY })
        if (capabilities.isNotEmpty()) {
            add(KEY_CAPABILITIES, JsonArray().apply { capabilities.distinct().forEach(::add) })
        }
        if (e2eKey != null) {
            add(KEY_E2E_KEY, e2eKey.toWire())
        }
        keepaliveSeconds?.let { addProperty(KEY_KEEPALIVE_SECONDS, it) }
    }

    private fun helloEnvelope(entry: DeviceExchangeAccountEntry, token: String, keepaliveSeconds: Int?) =
        DeviceExchangeEnvelope.of(DeviceExchangeEnvelope.TYPE_HELLO) {
            addProperty(KEY_DEVICE_ID, entry.deviceId)
            addProperty(KEY_DEVICE_TOKEN, token)
            addProperty(KEY_PRODUCT_VERSION, product.name)
            keepaliveSeconds?.let { addProperty(KEY_KEEPALIVE_SECONDS, it) }
        }

    companion object {
        val AUTHENTICATION_REASONS = setOf("signed-out", "device-revoked", "bad-credentials")
        const val KEY_DEVICE_ID = "deviceId"
        const val KEY_DEVICE_TOKEN = "deviceToken"
        const val KEY_KEEPALIVE_SECONDS = "keepaliveSeconds"
        const val REASON_UNKNOWN = "unknown"

        private const val KEY_LOGIN = "login"
        private const val KEY_PASSWORD = "password"
        private const val KEY_PAIRING_CODE = "pairingCode"
        private const val KEY_DEVICE_NAME = "deviceName"
        private const val KEY_PRODUCT = "product"
        private const val KEY_PRODUCT_VERSION = "productVersion"
        private const val KEY_PLATFORM = "platform"
        private const val KEY_ROLES = "roles"
        private const val KEY_RECEIVER = "receiver"
        private const val KEY_CAPABILITIES = "capabilities"
        private const val KEY_E2E_KEY = "e2eKey"

        fun hashId(id: String): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(id.toByteArray(Charsets.UTF_8))
            return digest.take(4).joinToString("") { "%02x".format(it) }
        }
    }
}
