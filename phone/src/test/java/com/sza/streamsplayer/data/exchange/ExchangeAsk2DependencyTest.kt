package com.sza.streamsplayer.data.exchange

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Ask 2 dependency boundary test per contract DEVICE-EXCHANGE S0016 Scope 9:
 * The session and directory code (`exchange.session`, `exchange.directory`) imports nothing
 * of `broadcast`, no `MediaSession`, no Wear code.
 * Fails the build if an unauthorized coupling is introduced.
 */
class ExchangeAsk2DependencyTest {

    @Test
    fun `session and directory packages import no broadcast no MediaSession and no Wear code`() {
        val rootDirs = listOf(
            File("../exchange-core/src/main/kotlin/com/sza/streamsplayer/data/exchange/session"),
            File("../exchange-core/src/main/kotlin/com/sza/streamsplayer/data/exchange/directory"),
            File("exchange-core/src/main/kotlin/com/sza/streamsplayer/data/exchange/session"),
            File("exchange-core/src/main/kotlin/com/sza/streamsplayer/data/exchange/directory"),
            File("src/main/java/com/sza/streamsplayer/data/exchange/session"),
            File("src/main/java/com/sza/streamsplayer/data/exchange/directory"),
            File("phone/src/main/java/com/sza/streamsplayer/data/exchange/session"),
            File("phone/src/main/java/com/sza/streamsplayer/data/exchange/directory"),
        ).filter { it.isDirectory }

        assertTrue("Should find session or directory package directory", rootDirs.isNotEmpty())

        val sourceFiles = rootDirs.flatMap { dir ->
            dir.walkTopDown().filter { it.extension == "kt" }.toList()
        }.distinctBy { it.name }

        assertTrue("Should inspect at least 5 source files across session and directory", sourceFiles.size >= 5)

        val forbiddenPatterns = listOf(
            Regex("""import\s+com\.sza\.streamsplayer\.broadcast\b"""),
            Regex("""import\s+android\.media\.session\b"""),
            Regex("""import\s+androidx\.media3\.session\b"""),
            Regex("""import\s+.*MediaSession\b"""),
            Regex("""import\s+com\.sza\.streamsplayer\.wear\b"""),
            Regex("""import\s+com\.google\.android\.gms\.wearable\b"""),
        )

        val violations = mutableListOf<String>()

        for (file in sourceFiles) {
            val lines = file.readLines()
            for ((index, line) in lines.withIndex()) {
                for (pattern in forbiddenPatterns) {
                    if (pattern.containsMatchIn(line)) {
                        violations.add("${file.name}:${index + 1} -> $line")
                    }
                }
            }
        }

        assertTrue("Forbidden imports found in session/directory packages:\n${violations.joinToString("\n")}", violations.isEmpty())
    }
}
