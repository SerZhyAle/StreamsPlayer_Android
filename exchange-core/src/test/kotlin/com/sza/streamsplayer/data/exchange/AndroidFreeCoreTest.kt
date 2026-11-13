package com.sza.streamsplayer.data.exchange

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AndroidFreeCoreTest {
    @Test fun `the shared core imports no Android platform or app implementation`() {
        val sources = File("src/main/kotlin").walkTopDown().filter { it.extension == "kt" }.toList()
        assertTrue("Expected the extracted client, not an empty directory", sources.size >= 35)
        val forbidden = Regex("(?m)^import\\s+(android\\.|androidx\\.|dagger\\.hilt\\.|com\\.google\\.android\\.|com\\.sza\\.streamsplayer\\.wear\\.)")
        val violations = sources.filter { forbidden.containsMatchIn(it.readText()) }
        assertTrue("Platform imports: $violations", violations.isEmpty())
    }
}
