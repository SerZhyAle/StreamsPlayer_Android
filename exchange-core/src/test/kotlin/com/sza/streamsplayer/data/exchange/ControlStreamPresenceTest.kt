package com.sza.streamsplayer.data.exchange

import com.sza.streamsplayer.data.exchange.session.*
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.Socket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean

class ControlStreamPresenceTest {
    private val entry = DeviceExchangeAccountEntry("exchange.example", 443, "owner", "watch-device")

    @Test fun `closing the visible holder sends bye and releases a blocked socket with no service holder`() = runBlocking {
        val client = mockk<DeviceExchangeAccountClient>()
        val socket = BlockingSocket()
        coEvery { client.openAuthenticated(any(), any()) } returns session(socket)
        val control = DeviceExchangeControlStream(client)
        try {
            control.setEntry(entry)
            delay(30)
            coVerify(exactly = 0) { client.openAuthenticated(any(), any()) }
            control.updateActivityForeground(true)
            await { control.isConnected }
            control.updateActivityForeground(false)
            assertFalse(control.isConnected)
            assertTrue(socket.ended.get())
            val bytes = ByteArrayInputStream(socket.written.toByteArray())
            assertEquals("bye", DeviceExchangeFrameCodec.read(bytes)?.type)
            delay(50)
            coVerify(exactly = 1) { client.openAuthenticated(any(), any()) }
        } finally { control.close() }
    }

    @Test fun `a connection finishing after the screen closes is released instead of installing stale presence`() = runBlocking {
        val client = mockk<DeviceExchangeAccountClient>()
        val started = CompletableDeferred<Unit>()
        val release = CountDownLatch(1)
        val socket = BlockingSocket()
        coEvery { client.openAuthenticated(any(), any()) } coAnswers {
            started.complete(Unit)
            release.await()
            session(socket)
        }
        val control = DeviceExchangeControlStream(client)
        try {
            control.setEntry(entry)
            control.updateActivityForeground(true)
            withTimeout(2_000) { started.await() }
            control.updateActivityForeground(false)
            release.countDown()
            await { socket.ended.get() }
            assertFalse(control.isConnected)
        } finally { release.countDown(); control.close() }
    }

    @Test fun `re-reading the same entry cannot silently retry a revoked token`() = runBlocking {
        val client = mockk<DeviceExchangeAccountClient>()
        coEvery { client.openAuthenticated(any(), any()) } throws DeviceExchangeRefusedException("device-revoked")
        val control = DeviceExchangeControlStream(client)
        try {
            control.setEntry(entry)
            control.updateActivityForeground(true)
            await { control.signInReason.value == "device-revoked" }
            control.setEntry(entry)
            control.updateActivityForeground(true)
            delay(50)
            coVerify(exactly = 1) { client.openAuthenticated(any(), any()) }
        } finally { control.close() }
    }

    private suspend fun await(predicate: () -> Boolean) = withTimeout(2_000) {
        while (!predicate()) delay(5)
    }

    private fun session(socket: Socket) = DeviceExchangeSession(ExchangeConnection(socket, "SHA256:test"),
        DeviceExchangeWelcome.from(DeviceExchangeEnvelope.of("welcome")))

    private class BlockingSocket : Socket() {
        val ended = AtomicBoolean(false)
        val written = ByteArrayOutputStream()
        private val closedSignal = CountDownLatch(1)
        private val input = object : InputStream() {
            override fun read(): Int { closedSignal.await(); return -1 }
        }
        override fun getInputStream() = input
        override fun getOutputStream() = written
        override fun close() { ended.set(true); closedSignal.countDown() }
    }
}
