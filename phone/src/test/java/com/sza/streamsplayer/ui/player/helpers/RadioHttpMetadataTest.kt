package com.sza.streamsplayer.ui.player.helpers

import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@UnstableApi
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class RadioHttpMetadataTest {
    @Test
    fun radioOptsIntoIcyWithoutChangingOtherCallers() {
        MockWebServer().use { server ->
            server.start()
            for (requestMetadata in listOf(false, true)) {
                server.enqueue(MockResponse().setHeader("icy-metaint", "8192").setBody("audio"))
                val source = StreamDataSourceFactoryProvider.create(RuntimeEnvironment.getApplication(),
                    requestIcyMetadata = requestMetadata).createDataSource()
                try {
                    source.open(DataSpec(Uri.parse(server.url("/radio").toString())))
                    assertEquals(listOf("8192"), source.responseHeaders.entries
                        .first { it.key.equals("icy-metaint", ignoreCase = true) }.value)
                    assertEquals(if (requestMetadata) "1" else "0", server.takeRequest().getHeader("Icy-MetaData"))
                } finally {
                    source.close()
                }
            }
        }
    }
}
