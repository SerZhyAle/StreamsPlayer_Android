package com.sza.streamsplayer.data.repository

import com.sza.streamsplayer.domain.delivery.DeliveryAssets
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream

class LiveStreamCatalogIntegrationTest {

    @Test
    fun fetchAndParseLiveStreamCatalogZip() {
        val client = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()

        val request = Request.Builder()
            .url(DeliveryAssets.STREAM_CATALOG_URL)
            .build()

        val response = client.newCall(request).execute()
        assertTrue("HTTP request failed: ${response.code}", response.isSuccessful)
        val bodyBytes = response.body?.bytes()
        assertNotNull("Response body is null", bodyBytes)
        assertTrue("Archive body too small: ${bodyBytes!!.size}", bodyBytes.size > 1_000_000)

        // Find streams.csv in zip
        var streamsCsv: String? = null
        ZipInputStream(ByteArrayInputStream(bodyBytes)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (entry.name.endsWith("streams.csv")) {
                    streamsCsv = String(zip.readBytes(), Charsets.UTF_8)
                    break
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }

        assertNotNull("streams.csv entry missing from archive", streamsCsv)

        val parser = StreamCatalogCsvParser()
        val parsed = parser.parse(streamsCsv!!)
        assertTrue("Expected thousands of channels, found: ${parsed.size}", parsed.size > 5000)
    }
}
