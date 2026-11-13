package com.sza.streamsplayer.domain.model

import com.google.gson.Gson
import org.junit.Assert.*
import org.junit.Test

class WearOpenOnPhonePayloadTest {
    private val gson = Gson()
    @Test fun pathlessVideoRetainsKindAndName() {
        val request = WearOpenOnPhoneRequest("https://example.org/live", "Live video", "VIDEO")
        val encoded = gson.toJson(request)
        assertTrue(encoded.contains("\"mediaKind\":\"VIDEO\""))
        assertEquals(request, gson.fromJson(encoded, WearOpenOnPhoneRequest::class.java))
    }
    @Test fun olderRequestWithoutKindStillDecodes() {
        val request = gson.fromJson("""{"token":"https://example.org/radio","displayName":"Radio"}""", WearOpenOnPhoneRequest::class.java)
        assertNull(request.mediaKind)
        assertEquals("Radio", request.displayName)
    }
}
