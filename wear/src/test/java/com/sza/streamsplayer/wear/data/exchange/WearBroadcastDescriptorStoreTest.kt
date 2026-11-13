package com.sza.streamsplayer.wear.data.exchange

import android.content.Context
import com.sza.streamsplayer.data.broadcast.BroadcastDescriptorDto
import com.sza.streamsplayer.data.exchange.security.AndroidKeyStoreCipher
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class WearBroadcastDescriptorStoreTest {
    private val prefs = WatchExchangeTestPreferences()
    private val key = WatchExchangeTestCipher()
    private val context = mockk<Context>()
    private val cipher = mockk<AndroidKeyStoreCipher>()
    private val descriptor = BroadcastDescriptorDto(url = "https://exchange.example/v2/b/capability/stream", mode = "AUDIO_ONLY")

    init {
        every { context.getSharedPreferences(any(), any()) } returns prefs.prefs
        every { cipher.encrypt(any()) } answers { key.encrypt(firstArg()) }
        every { cipher.decrypt(any(), any()) } answers { key.decrypt(firstArg(), secondArg()) }
    }

    @Test fun `the handed descriptor survives reopening without plaintext capabilities in preferences`() = runTest {
        val store = WearBroadcastDescriptorStore(context, cipher)
        assertTrue(store.save(descriptor.url, descriptor))
        assertFalse(prefs.values.toString().contains("capability"))
        assertEquals(descriptor, WearBroadcastDescriptorStore(context, cipher).get(descriptor.url))
    }

    @Test fun `key loss is unavailable rather than a bare URL fallback`() = runTest {
        val store = WearBroadcastDescriptorStore(context, cipher)
        store.save(descriptor.url, descriptor)
        key.keyLost = true
        try { store.get(descriptor.url); throw AssertionError("Expected unavailable") }
        catch (_: IOException) { }
        assertNull(store.get("https://ordinary-radio.example"))
    }
}
