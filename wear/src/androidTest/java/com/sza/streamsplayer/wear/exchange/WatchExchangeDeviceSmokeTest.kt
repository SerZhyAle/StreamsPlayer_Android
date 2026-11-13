package com.sza.streamsplayer.wear.exchange

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sza.streamsplayer.data.exchange.session.DeviceExchangeEnrollResult
import com.sza.streamsplayer.wear.data.exchange.WearExchangeJoin
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Run only against a disposable local fake server; never overwrites an existing exchange account. */
@RunWith(AndroidJUnit4::class)
class WatchExchangeDeviceSmokeTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val graph get() = EntryPointAccessors.fromApplication(
        instrumentation.targetContext.applicationContext, WatchExchangeSmokeEntryPoint::class.java)

    @Test fun enroll() = runBlocking {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue(args.getString("localFakeServer") == "true")
        assertNull("Use a disposable watch install without an existing account", graph.entries().getEntry())
        val result = graph.accounts().enroll(WearExchangeJoin(
            host = "127.0.0.1", port = checkNotNull(args.getString("serverPort")).toInt(), login = "smoke-owner",
            pinnedCertificate = checkNotNull(args.getString("serverFingerprint")), pairingCode = "7KQ2M9XA",
        ))
        assertTrue("The real watch enrollment did not succeed", result is DeviceExchangeEnrollResult.Enrolled)
        val entry = graph.entries().getEntry()!!
        assertEquals(16, java.util.Base64.getUrlDecoder().decode(entry.deviceId).size)
        assertFalse(graph.entries().token(entry).isNullOrEmpty())
    }

    @Test fun remove() = runBlocking {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue(args.getString("localFakeServer") == "true")
        val entry = graph.entries().getEntry() ?: throw AssertionError("No smoke account to remove")
        assertEquals("smoke-owner", entry.login)
        assertTrue(graph.accounts().remove(entry.deviceId))
        assertNull(graph.entries().getEntry())
        assertNull(graph.entries().token(entry))
    }
}
