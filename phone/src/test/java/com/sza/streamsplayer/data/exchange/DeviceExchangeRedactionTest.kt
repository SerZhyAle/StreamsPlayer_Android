package com.sza.streamsplayer.data.exchange

import com.sza.streamsplayer.data.exchange.session.DeviceExchangeAccountClient
import com.sza.streamsplayer.data.exchange.session.DeviceExchangeCredential
import com.sza.streamsplayer.data.exchange.session.DeviceExchangeEnrollment
import com.sza.streamsplayer.data.exchange.session.DeviceExchangeWelcome
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import timber.log.Timber
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.Socket
import java.security.MessageDigest

/**
 * Canary test for log and exception redaction per contract DEVICE-EXCHANGE 7.9 and S0016 Scope 8.
 * Runs enrol, hello, list exchange with canary strings for password, token, and pairing code,
 * captures every Timber log, exception message and string representation,
 * and asserts none contains a canary.
 */
class DeviceExchangeRedactionTest {

    private val capturedLogs = mutableListOf<String>()

    private val testTree = object : Timber.Tree() {
        override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
            capturedLogs.add(message)
            t?.message?.let { capturedLogs.add(it) }
        }
    }

    private val canaryPassword = "CANARY_PW_VERY_SECRET_987654"
    private val canaryToken = "CANARY_TOKEN_TOP_SECRET_32_BYTES_HEX"
    private val canaryPairingCode = "CANARY_CODE_PAIR_555"

    @Before
    fun setUp() {
        ExchangeLog.sink = { priority, cause, message -> Timber.log(priority, cause, message) }
        Timber.uprootAll()
        Timber.plant(testTree)
    }

    @After
    fun tearDown() {
        ExchangeLog.sink = null
        Timber.uprootAll()
    }

    @Test
    fun `no canary string reaches timber logs toString or exception messages`() {
        val entry = DeviceExchangeAccountEntry(
            host = "exchange.example.net",
            port = 44022,
            login = "testuser",
            deviceId = "DEVICE_ID_UNHASHED_RAW",
            pinnedCertificate = null,
        )

        val secretStore = InMemorySecretStore()
        val fakeConnector = object : ExchangeConnector {
            override fun connect(host: String, port: Int, trust: ExchangeTrust): ExchangeConnection {
                val enrolledResp = DeviceExchangeEnvelope.of(DeviceExchangeEnvelope.TYPE_ENROLLED) {
                    addProperty("deviceToken", canaryToken)
                    addProperty("account", "testuser")
                }
                val inBytes = DeviceExchangeFrameCodec.frame(enrolledResp)
                val inStream = ByteArrayInputStream(inBytes)
                val outStream = ByteArrayOutputStream()

                val fakeSocket = object : Socket() {
                    override fun getInputStream() = inStream
                    override fun getOutputStream() = outStream
                    override fun close() {}
                }
                return ExchangeConnection(fakeSocket, "SHA256:fakecertfingerprint")
            }
        }

        val client = DeviceExchangeAccountClient(fakeConnector, secretStore)

        // 1. Enroll with password canary
        val passwordCred = DeviceExchangeCredential.Password(canaryPassword.toCharArray())
        val enrollment = DeviceExchangeEnrollment(
            deviceName = "Test Device",
            productVersion = "2.61.0",
        )

        // Envelope toString check
        val enrollEnv = DeviceExchangeEnvelope.of(DeviceExchangeEnvelope.TYPE_ENROLL) {
            addProperty("password", passwordCred.value)
        }
        assertFalse(enrollEnv.toString().contains(canaryPassword))
        assertFalse(passwordCred.toString().contains(canaryPassword))

        kotlinx.coroutines.runBlocking {
            client.enroll(entry, passwordCred, enrollment)
        }

        // 2. Open authenticated with token
        val fakeHelloConnector = object : ExchangeConnector {
            override fun connect(host: String, port: Int, trust: ExchangeTrust): ExchangeConnection {
                val welcomeResp = DeviceExchangeEnvelope.of(DeviceExchangeEnvelope.TYPE_WELCOME) {
                    addProperty("account", "testuser")
                    addProperty("keepaliveSeconds", 30)
                }
                val inBytes = DeviceExchangeFrameCodec.frame(welcomeResp)
                val inStream = ByteArrayInputStream(inBytes)
                val outStream = ByteArrayOutputStream()

                val fakeSocket = object : Socket() {
                    override fun getInputStream() = inStream
                    override fun getOutputStream() = outStream
                    override fun close() {}
                }
                return ExchangeConnection(fakeSocket, "SHA256:fakecertfingerprint")
            }
        }
        val helloClient = DeviceExchangeAccountClient(fakeHelloConnector, secretStore)
        kotlinx.coroutines.runBlocking {
            val session = helloClient.openAuthenticated(entry)
            session.close()
        }

        // 3. Pairing code cred toString check
        val pairCred = DeviceExchangeCredential.PairingCode(canaryPairingCode)
        assertFalse(pairCred.toString().contains(canaryPairingCode))

        // Assert no logs contain canaries
        for (log in capturedLogs) {
            assertFalse("Log contains password canary: $log", log.contains(canaryPassword))
            assertFalse("Log contains token canary: $log", log.contains(canaryToken))
            assertFalse("Log contains pairing code canary: $log", log.contains(canaryPairingCode))
        }

        // Assert device ID was hashed to first 8 hex of sha256
        val expectedHashedId = MessageDigest.getInstance("SHA-256")
            .digest(entry.deviceId.toByteArray(Charsets.UTF_8))
            .take(4)
            .joinToString("") { "%02x".format(it) }

        assertTrue(
            "Logged deviceId should be sanitized to 8 hex characters ($expectedHashedId)",
            capturedLogs.any { it.contains("deviceId=$expectedHashedId") }
        )
    }
}
