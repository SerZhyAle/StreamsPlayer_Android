package com.sza.streamsplayer.data.exchange

import com.sza.streamsplayer.data.exchange.ExchangeConnectionString.Companion.readConnectionStringFile
import com.sza.streamsplayer.data.exchange.ExchangeConnectionString.Outcome
import com.sza.streamsplayer.data.exchange.ExchangeConnectionString.Rules
import com.sza.streamsplayer.data.exchange.errorMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream

/**
 * Unit tests verifying [ExchangeConnectionString] against the 14 grammar proposal points
 * and Ask 1 of the exchange connection string proposal.
 */
class ExchangeConnectionStringTest {

    @org.junit.Test
    fun `invitation is validated but never kept or encoded by this sign in only product`() {
        val invitation = DeviceIdGenerator.generate()
        val parsed = ExchangeConnectionString.parse("FMSEXCH1:x=example.net%3A443&u=alice&i=$invitation")
            as ExchangeConnectionString.Outcome.Valid
        org.junit.Assert.assertTrue(parsed.isInvitation)
        org.junit.Assert.assertFalse(parsed.toString().contains(invitation))
        org.junit.Assert.assertFalse(ExchangeConnectionString.encode(parsed).contains("i="))
        org.junit.Assert.assertTrue(ExchangeConnectionString.parse("FMSEXCH1:x=example.net%3A443&i=short") is ExchangeConnectionString.Outcome.Refused)
        org.junit.Assert.assertTrue(ExchangeConnectionString.parse("FMSEXCH1:x=example.net%3A443&i=$invitation&i=$invitation") is ExchangeConnectionString.Outcome.Refused)
    }

    // --- Proposal Ask 1, Grammar point 11: Prefix and Major Version ---

    @Test
    fun `point 11 valid prefix FMSEXCH1 is accepted case-sensitively`() {
        val input = "FMSEXCH1:x=192.168.1.10%3A8080"
        val outcome = ExchangeConnectionString.parse(input)
        assertTrue(outcome is Outcome.Valid)
        assertEquals("192.168.1.10:8080", (outcome as Outcome.Valid).hostPort)
    }

    @Test
    fun `point 11 lowercase prefix fmsexch1 is refused`() {
        val input = "fmsexch1:x=192.168.1.10%3A8080"
        val outcome = ExchangeConnectionString.parse(input)
        assertEquals(Outcome.Refused(Rules.PREFIX_CASE_SENSITIVE), outcome)
    }

    @Test
    fun `point 11 different major version returns UnsupportedMajor`() {
        val outcome2 = ExchangeConnectionString.parse("FMSEXCH2:x=192.168.1.10%3A8080")
        assertEquals(Outcome.UnsupportedMajor("FMSEXCH2:"), outcome2)

        val outcome0 = ExchangeConnectionString.parse("FMSEXCH0:x=192.168.1.10%3A8080")
        assertEquals(Outcome.Refused(Rules.PREFIX_CASE_SENSITIVE), outcome0)

        val outcome9 = ExchangeConnectionString.parse("FMSEXCH9:x=192.168.1.10%3A8080")
        assertEquals(Outcome.UnsupportedMajor("FMSEXCH9:"), outcome9)
    }

    @Test
    fun `point 11 recognized other families return NotExchange`() {
        val sftp1 = ExchangeConnectionString.parse("FMSSFTP1:x=192.168.1.10%3A22")
        assertEquals(Outcome.NotExchange("FMSSFTP1"), sftp1)

        val sftp2 = ExchangeConnectionString.parse("FMSSFTP2:x=192.168.1.10%3A22&sid=abc")
        assertEquals(Outcome.NotExchange("FMSSFTP2"), sftp2)

        val bcast = ExchangeConnectionString.parse("FMSBCAST1:H4sIC...")
        assertEquals(Outcome.NotExchange("FMSBCAST1"), bcast)

        val jsonDesc = ExchangeConnectionString.parse("{\"schemaVersion\":1,\"stream\":{}}")
        assertEquals(Outcome.NotExchange("broadcast descriptor"), jsonDesc)

        val bareHttp = ExchangeConnectionString.parse("http://example.com/stream.m3u8")
        assertEquals(Outcome.NotExchange("URL"), bareHttp)

        val bareHttps = ExchangeConnectionString.parse("https://example.com/live")
        assertEquals(Outcome.NotExchange("URL"), bareHttps)

        val bareRtsp = ExchangeConnectionString.parse("rtsp://cam.local:554/live")
        assertEquals(Outcome.NotExchange("URL"), bareRtsp)
    }

    @Test
    fun `point 11 arbitrary non-exchange text is refused`() {
        val outcome = ExchangeConnectionString.parse("random clipboard garbage")
        assertEquals(Outcome.Refused(Rules.PREFIX_CASE_SENSITIVE), outcome)
    }

    // --- Proposal Ask 1, Grammar point 1, 9: Trimming and Whitespace ---

    @Test
    fun `point 1 and 9 leading BOM and surrounding whitespace are stripped`() {
        val input = "\uFEFF  \r\n  FMSEXCH1:x=192.168.1.10%3A8080  \t\n  "
        val outcome = ExchangeConnectionString.parse(input)
        assertTrue(outcome is Outcome.Valid)
        assertEquals("192.168.1.10:8080", (outcome as Outcome.Valid).hostPort)
    }

    @Test
    fun `point 1 internal whitespace is refused`() {
        val withSpace = "FMSEXCH1: x=192.168.1.10%3A8080"
        assertEquals(Outcome.Refused(Rules.NO_INTERNAL_WHITESPACE), ExchangeConnectionString.parse(withSpace))

        val withTab = "FMSEXCH1:x=192.168.1.10%3A8080\t&n=Test"
        assertEquals(Outcome.Refused(Rules.NO_INTERNAL_WHITESPACE), ExchangeConnectionString.parse(withTab))

        val withNewline = "FMSEXCH1:x=192.168.1.10%3A8080\n&n=Test"
        assertEquals(Outcome.Refused(Rules.NO_INTERNAL_WHITESPACE), ExchangeConnectionString.parse(withNewline))
    }

    @Test
    fun `point 1 length over 512 characters on trimmed text is refused`() {
        val valid512 = "FMSEXCH1:x=192.168.1.10%3A8080&n=" + "a".repeat(512 - "FMSEXCH1:x=192.168.1.10%3A8080&n=".length)
        assertEquals(512, valid512.length)
        // Note: 'n' cannot be over 64 characters so this would fail name syntax if parsed through, but let's test length specifically:
        val longUnknownKey = "FMSEXCH1:x=192.168.1.10%3A8080&unknown=" + "a".repeat(513 - "FMSEXCH1:x=192.168.1.10%3A8080&unknown=".length)
        assertEquals(513, longUnknownKey.length)
        assertEquals(Outcome.Refused(Rules.MAX_LENGTH_512), ExchangeConnectionString.parse(longUnknownKey))
    }

    // --- Proposal Ask 1, Grammar point 4: Fields and Keys ---

    @Test
    fun `point 4 empty fields and fields without equals sign are refused`() {
        val emptyField = "FMSEXCH1:x=192.168.1.10%3A8080&&n=Test"
        assertEquals(Outcome.Refused(Rules.FIELD_SYNTAX), ExchangeConnectionString.parse(emptyField))

        val leadingAmp = "FMSEXCH1:&x=192.168.1.10%3A8080"
        assertEquals(Outcome.Refused(Rules.FIELD_SYNTAX), ExchangeConnectionString.parse(leadingAmp))

        val trailingAmp = "FMSEXCH1:x=192.168.1.10%3A8080&"
        assertEquals(Outcome.Refused(Rules.FIELD_SYNTAX), ExchangeConnectionString.parse(trailingAmp))

        val noEquals = "FMSEXCH1:x=192.168.1.10%3A8080&invalidfield"
        assertEquals(Outcome.Refused(Rules.FIELD_SYNTAX), ExchangeConnectionString.parse(noEquals))

        val emptyKey = "FMSEXCH1:x=192.168.1.10%3A8080&=value"
        assertEquals(Outcome.Refused(Rules.FIELD_SYNTAX), ExchangeConnectionString.parse(emptyKey))
    }

    @Test
    fun `point 4 known key repeated is refused`() {
        val dupX = "FMSEXCH1:x=192.168.1.10%3A8080&x=192.168.1.20%3A8080"
        assertEquals(Outcome.Refused(Rules.NO_DUPLICATE_KNOWN_KEYS), ExchangeConnectionString.parse(dupX))

        val dupN = "FMSEXCH1:x=192.168.1.10%3A8080&n=First&n=Second"
        assertEquals(Outcome.Refused(Rules.NO_DUPLICATE_KNOWN_KEYS), ExchangeConnectionString.parse(dupN))
    }

    @Test
    fun `point 4 empty value of known key is refused`() {
        val emptyN = "FMSEXCH1:x=192.168.1.10%3A8080&n="
        assertEquals(Outcome.Refused(Rules.NAME_SYNTAX), ExchangeConnectionString.parse(emptyN))

        val emptyU = "FMSEXCH1:x=192.168.1.10%3A8080&u="
        assertEquals(Outcome.Refused(Rules.LOGIN_SYNTAX), ExchangeConnectionString.parse(emptyU))

        val emptyFp = "FMSEXCH1:x=192.168.1.10%3A8080&fp="
        assertEquals(Outcome.Refused(Rules.FINGERPRINT_SYNTAX), ExchangeConnectionString.parse(emptyFp))
    }

    @Test
    fun `point 4 unknown key-value pair is ignored`() {
        val input = "FMSEXCH1:x=192.168.1.10%3A8080&futureFeature=enabled&version=2"
        val outcome = ExchangeConnectionString.parse(input)
        assertTrue(outcome is Outcome.Valid)
        assertEquals("192.168.1.10:8080", (outcome as Outcome.Valid).hostPort)
        assertEquals("FMSEXCH1:x=192.168.1.10%3A8080", ExchangeConnectionString.encode(outcome))
    }

    // --- Proposal Grammar point 8: Field Order ---

    @Test
    fun `point 8 field order is tolerated and encoded canonically as x, fp, n, u`() {
        val unordered = "FMSEXCH1:u=alice&n=Studio&fp=SHA256%3AAEBgcICAgICAgICAgICAgICAgICAgICAgICAgICAgIA&x=10.0.0.1%3A443"
        val outcome = ExchangeConnectionString.parse(unordered)
        assertTrue(outcome is Outcome.Valid)
        val valid = outcome as Outcome.Valid
        assertEquals("10.0.0.1:443", valid.hostPort)
        assertEquals("SHA256:AEBgcICAgICAgICAgICAgICAgICAgICAgICAgICAgIA", valid.fingerprint)
        assertEquals("Studio", valid.name)
        assertEquals("alice", valid.login)

        val encoded = ExchangeConnectionString.encode(valid)
        assertEquals(
            "FMSEXCH1:x=10.0.0.1%3A443&fp=SHA256%3AAEBgcICAgICAgICAgICAgICAgICAgICAgICAgICAgIA&n=Studio&u=alice",
            encoded
        )
    }

    // --- Proposal Grammar point 5, 14: Percent-Encoding and Strict UTF-8 ---

    @Test
    fun `point 5 percent decoding handles plus as space and lowercase hex`() {
        val input = "FMSEXCH1:x=10.0.0.1%3a80&n=My+Test+Server%21"
        val outcome = ExchangeConnectionString.parse(input)
        assertTrue(outcome is Outcome.Valid)
        val valid = outcome as Outcome.Valid
        assertEquals("My Test Server!", valid.name)
        assertEquals("FMSEXCH1:x=10.0.0.1%3A80&n=My+Test+Server%21", ExchangeConnectionString.encode(valid))
    }

    @Test
    fun `point 5 raw non-ASCII character in query is refused`() {
        val input = "FMSEXCH1:x=10.0.0.1%3A80&n=Сервер"
        assertEquals(Outcome.Refused(Rules.PERCENT_ENCODING), ExchangeConnectionString.parse(input))
    }

    @Test
    fun `point 5 malformed percent encoding is refused`() {
        val danglingPercent = "FMSEXCH1:x=10.0.0.1%3A80&n=Test%"
        assertEquals(Outcome.Refused(Rules.PERCENT_ENCODING), ExchangeConnectionString.parse(danglingPercent))

        val singleHex = "FMSEXCH1:x=10.0.0.1%3A80&n=Test%2"
        assertEquals(Outcome.Refused(Rules.PERCENT_ENCODING), ExchangeConnectionString.parse(singleHex))

        val nonHex = "FMSEXCH1:x=10.0.0.1%3A80&n=Test%2G"
        assertEquals(Outcome.Refused(Rules.PERCENT_ENCODING), ExchangeConnectionString.parse(nonHex))
    }

    // --- Proposal Grammar point 2, 3: Host and Port (x) ---

    @Test
    fun `point 2 missing x is refused`() {
        val input = "FMSEXCH1:n=OnlyName"
        assertEquals(Outcome.Refused(Rules.HOST_PORT_REQUIRED), ExchangeConnectionString.parse(input))
    }

    @Test
    fun `point 2 IPv4 host dotted quad is parsed`() {
        val input = "FMSEXCH1:x=192.168.1.1%3A8080"
        val outcome = ExchangeConnectionString.parse(input)
        assertTrue(outcome is Outcome.Valid)
        assertEquals("192.168.1.1:8080", (outcome as Outcome.Valid).hostPort)
    }

    @Test
    fun `point 2 digits and dots non-IPv4 is refused, never treated as DNS`() {
        val invalidOctet = "FMSEXCH1:x=192.168.1.300%3A8080"
        assertEquals(Outcome.Refused(Rules.HOST_SYNTAX), ExchangeConnectionString.parse(invalidOctet))

        val threeOctets = "FMSEXCH1:x=192.168.1%3A8080"
        assertEquals(Outcome.Refused(Rules.HOST_SYNTAX), ExchangeConnectionString.parse(threeOctets))

        val leadingZeroOctet = "FMSEXCH1:x=192.168.01.1%3A8080"
        assertEquals(Outcome.Refused(Rules.HOST_SYNTAX), ExchangeConnectionString.parse(leadingZeroOctet))
    }

    @Test
    fun `point 2 DNS hostname is parsed case-insensitively and canonicalized lowercase`() {
        val input = "FMSEXCH1:x=MediaServer.Local.Example%3A9000"
        val outcome = ExchangeConnectionString.parse(input)
        assertTrue(outcome is Outcome.Valid)
        assertEquals("mediaserver.local.example:9000", (outcome as Outcome.Valid).hostPort)
    }

    @Test
    fun `point 2 invalid DNS host is refused`() {
        val trailingDot = "FMSEXCH1:x=example.com.%3A8080"
        assertEquals(Outcome.Refused(Rules.HOST_SYNTAX), ExchangeConnectionString.parse(trailingDot))

        val hyphenStart = "FMSEXCH1:x=-example.com%3A8080"
        assertEquals(Outcome.Refused(Rules.HOST_SYNTAX), ExchangeConnectionString.parse(hyphenStart))

        val withPath = "FMSEXCH1:x=example.com%2Fpath%3A8080"
        assertEquals(Outcome.Refused(Rules.HOST_SYNTAX), ExchangeConnectionString.parse(withPath))

        val withUser = "FMSEXCH1:x=user%40example.com%3A8080"
        assertEquals(Outcome.Refused(Rules.HOST_SYNTAX), ExchangeConnectionString.parse(withUser))
    }

    @Test
    fun `point 2 bracketed IPv6 literal without zone id is parsed`() {
        val input = "FMSEXCH1:x=%5B2001%3Adb8%3A%3A7%5D%3A22022"
        val outcome = ExchangeConnectionString.parse(input)
        assertTrue(outcome is Outcome.Valid)
        assertEquals("[2001:db8::7]:22022", (outcome as Outcome.Valid).hostPort)
    }

    @Test
    fun `point 2 IPv6 with zone id or without brackets is refused`() {
        val withZone = "FMSEXCH1:x=%5B2001%3Adb8%3A%3A7%25eth0%5D%3A22022"
        assertEquals(Outcome.Refused(Rules.HOST_SYNTAX), ExchangeConnectionString.parse(withZone))

        val withoutBrackets = "FMSEXCH1:x=2001%3Adb8%3A%3A7%3A22022"
        assertEquals(Outcome.Refused(Rules.HOST_SYNTAX), ExchangeConnectionString.parse(withoutBrackets))
    }

    @Test
    fun `point 3 port range and leading zeros`() {
        val portZero = "FMSEXCH1:x=10.0.0.1%3A0"
        assertEquals(Outcome.Refused(Rules.HOST_SYNTAX), ExchangeConnectionString.parse(portZero))

        val portTooLarge = "FMSEXCH1:x=10.0.0.1%3A65536"
        assertEquals(Outcome.Refused(Rules.HOST_SYNTAX), ExchangeConnectionString.parse(portTooLarge))

        val portLeadingZero = "FMSEXCH1:x=10.0.0.1%3A080"
        val outcome = ExchangeConnectionString.parse(portLeadingZero)
        assertTrue(outcome is Outcome.Valid)
        assertEquals("10.0.0.1:80", (outcome as Outcome.Valid).hostPort)
        assertEquals("FMSEXCH1:x=10.0.0.1%3A80", ExchangeConnectionString.encode(outcome))

        val missingPort = "FMSEXCH1:x=10.0.0.1"
        assertEquals(Outcome.Refused(Rules.HOST_SYNTAX), ExchangeConnectionString.parse(missingPort))
    }

    // --- Proposal Grammar point 6: Fingerprint (fp) ---

    @Test
    fun `point 6 valid SHA256 fingerprint with 43 base64 characters without padding`() {
        // 32 zero bytes in base64 without padding is 42 'A's + 'A' (bits 00 = A)
        val validFp = "SHA256:" + "A".repeat(43)
        val input = "FMSEXCH1:x=10.0.0.1%3A80&fp=SHA256%3A" + "A".repeat(43)
        val outcome = ExchangeConnectionString.parse(input)
        assertTrue(outcome is Outcome.Valid)
        assertEquals(validFp, (outcome as Outcome.Valid).fingerprint)
    }

    @Test
    fun `point 6 lowercase sha256 prefix is refused`() {
        val input = "FMSEXCH1:x=10.0.0.1%3A80&fp=sha256%3A" + "A".repeat(43)
        assertEquals(Outcome.Refused(Rules.FINGERPRINT_SYNTAX), ExchangeConnectionString.parse(input))
    }

    @Test
    fun `point 6 fingerprint with invalid length or padding is refused`() {
        val padded = "FMSEXCH1:x=10.0.0.1%3A80&fp=SHA256%3A" + "A".repeat(42) + "%3D"
        assertEquals(Outcome.Refused(Rules.FINGERPRINT_SYNTAX), ExchangeConnectionString.parse(padded))

        val shortFp = "FMSEXCH1:x=10.0.0.1%3A80&fp=SHA256%3A" + "A".repeat(42)
        assertEquals(Outcome.Refused(Rules.FINGERPRINT_SYNTAX), ExchangeConnectionString.parse(shortFp))

        val urlSafe = "FMSEXCH1:x=10.0.0.1%3A80&fp=SHA256%3A" + "A".repeat(42) + "-"
        assertEquals(Outcome.Refused(Rules.FINGERPRINT_SYNTAX), ExchangeConnectionString.parse(urlSafe))
    }

    @Test
    fun `point 6 fingerprint with stray bits in 43rd character is refused`() {
        // In base64, 'B' is 000001 (bits 1,0 are 01, which are stray bits!)
        val strayBitsFp = "FMSEXCH1:x=10.0.0.1%3A80&fp=SHA256%3A" + "A".repeat(42) + "B"
        assertEquals(Outcome.Refused(Rules.FINGERPRINT_SYNTAX), ExchangeConnectionString.parse(strayBitsFp))
    }

    // --- Proposal Grammar point 1: Display Name (n) ---

    @Test
    fun `point 1 display name up to 64 Unicode scalar values is accepted`() {
        val name64 = "А".repeat(64)
        val encodedName = canonicalPercentEncode(name64)
        val input = "FMSEXCH1:x=10.0.0.1%3A80&n=$encodedName"
        val outcome = ExchangeConnectionString.parse(input)
        assertTrue(outcome is Outcome.Valid)
        assertEquals(name64, (outcome as Outcome.Valid).name)
    }

    @Test
    fun `point 1 display name exceeding 64 code points is refused`() {
        val name65 = "А".repeat(65)
        val encodedName = canonicalPercentEncode(name65)
        val input = "FMSEXCH1:x=10.0.0.1%3A80&n=$encodedName"
        assertEquals(Outcome.Refused(Rules.NAME_SYNTAX), ExchangeConnectionString.parse(input))
    }

    @Test
    fun `point 1 display name with control character is refused`() {
        val nameWithControl = "Server\u0007Name" // BEL control character
        val encodedName = canonicalPercentEncode(nameWithControl)
        val input = "FMSEXCH1:x=10.0.0.1%3A80&n=$encodedName"
        assertEquals(Outcome.Refused(Rules.NAME_SYNTAX), ExchangeConnectionString.parse(input))
    }

    // --- Proposal Grammar point 7: Login Hint (u) ---

    @Test
    fun `point 7 login hint 3 to 64 chars of allowed set and re-encoded lowercase`() {
        val input = "FMSEXCH1:x=10.0.0.1%3A80&u=Alice.Admin_99-ok"
        val outcome = ExchangeConnectionString.parse(input)
        assertTrue(outcome is Outcome.Valid)
        val valid = outcome as Outcome.Valid
        assertEquals("alice.admin_99-ok", valid.login)
        assertEquals("FMSEXCH1:x=10.0.0.1%3A80&u=alice.admin_99-ok", ExchangeConnectionString.encode(valid))
    }

    @Test
    fun `point 7 login hint length constraints and invalid characters`() {
        val tooShort = "FMSEXCH1:x=10.0.0.1%3A80&u=al"
        assertEquals(Outcome.Refused(Rules.LOGIN_SYNTAX), ExchangeConnectionString.parse(tooShort))

        val tooLong = "FMSEXCH1:x=10.0.0.1%3A80&u=" + "a".repeat(65)
        assertEquals(Outcome.Refused(Rules.LOGIN_SYNTAX), ExchangeConnectionString.parse(tooLong))

        val invalidChar = "FMSEXCH1:x=10.0.0.1%3A80&u=alice%40work"
        assertEquals(Outcome.Refused(Rules.LOGIN_SYNTAX), ExchangeConnectionString.parse(invalidChar))
    }

    // --- Proposal Ask 1, Grammar point 9: File Reader ---

    @Test
    fun `point 9 file reader parses valid single-line file with optional trailing line break`() {
        val content = "FMSEXCH1:x=10.0.0.1%3A80\r\n".toByteArray(Charsets.UTF_8)
        val outcome = readConnectionStringFile(ByteArrayInputStream(content))
        assertTrue(outcome is Outcome.Valid)
        assertEquals("10.0.0.1:80", (outcome as Outcome.Valid).hostPort)
    }

    @Test
    fun `point 9 file reader strips BOM and accepts single line`() {
        val content = "\uFEFFFMSEXCH1:x=10.0.0.1%3A80\n".toByteArray(Charsets.UTF_8)
        val outcome = readConnectionStringFile(ByteArrayInputStream(content))
        assertTrue(outcome is Outcome.Valid)
        assertEquals("10.0.0.1:80", (outcome as Outcome.Valid).hostPort)
    }

    @Test
    fun `point 9 file reader refuses second non-empty line`() {
        val content = "FMSEXCH1:x=10.0.0.1%3A80\nsecond line\n".toByteArray(Charsets.UTF_8)
        val outcome = readConnectionStringFile(ByteArrayInputStream(content))
        assertEquals(Outcome.Refused(Rules.FILE_MULTIPLE_LINES), outcome)
    }

    @Test
    fun `point 9 file reader refuses file exceeding 4096 bytes without reading on`() {
        val content = ByteArray(4097) { 'a'.code.toByte() }
        val outcome = readConnectionStringFile(ByteArrayInputStream(content))
        assertEquals(Outcome.Refused(Rules.FILE_TOO_LARGE), outcome)
    }

    @Test
    fun `point 9 file reader names another family exactly as a pasted text does`() {
        listOf(
            "FMSSFTP1:abc" to "FMSSFTP1",
            "FMSSFTP2:AbCdEf" to "FMSSFTP2",
            "FMSSFTP3:AbCdEf" to "FMSSFTP3",
            "FMSBCAST1:H4sI" to "FMSBCAST1",
            "https://example.com/live" to "URL",
        ).forEach { (text, candidate) ->
            val fromFile = readConnectionStringFile(ByteArrayInputStream("$text\r\n".toByteArray(Charsets.UTF_8)))
            assertEquals(text, Outcome.NotExchange(candidate), fromFile)
            assertEquals(text, ExchangeConnectionString.parse(text), fromFile)
        }
    }

    @Test
    fun `point 11 a later family of share codes is named by its own prefix, never by a contract code`() {
        assertEquals(Outcome.NotExchange("FMSSFTP3"), ExchangeConnectionString.parse("FMSSFTP3:AbCdEf"))
        assertEquals(
            "This is not an exchange server connection string (looks like FMSSFTP3).",
            ExchangeConnectionString.parse("FMSSFTP3:AbCdEf").errorMessage()
        )
    }

    @Test
    fun `point 9 file reader refuses invalid UTF-8 and an empty file whole`() {
        val invalidUtf8 = "FMSEXCH1:x=10.0.0.1%3A80&n=".toByteArray(Charsets.UTF_8) + byteArrayOf(0xC3.toByte(), 0x28)
        assertTrue(readConnectionStringFile(ByteArrayInputStream(invalidUtf8)) is Outcome.Refused)
        assertTrue(readConnectionStringFile(ByteArrayInputStream(ByteArray(0))) is Outcome.Refused)
    }

    // --- Messages and TalkBack ---

    @Test
    fun `errorMessage returns TalkBack-safe strings naming contract on UnsupportedMajor`() {
        val outcomeMajor = Outcome.UnsupportedMajor("FMSEXCH2:")
        assertEquals("Unsupported DEVICE-EXCHANGE version (FMSEXCH2:). Please update the app.", outcomeMajor.errorMessage())

        val outcomeNotExchange = Outcome.NotExchange("FMSSFTP2")
        assertEquals("This is not an exchange server connection string (looks like FMSSFTP2).", outcomeNotExchange.errorMessage())

        val outcomeRefused = Outcome.Refused(Rules.PORT_RANGE)
        assertEquals("This is not a valid connection string.", outcomeRefused.errorMessage())

        val outcomeValid = Outcome.Valid("10.0.0.1:80")
        assertNull(outcomeValid.errorMessage())
    }

    private fun canonicalPercentEncode(value: String): String {
        val bytes = value.toByteArray(Charsets.UTF_8)
        val sb = StringBuilder()
        for (b in bytes) {
            val c = (b.toInt() and 0xFF).toChar()
            if (c in 'A'..'Z' || c in 'a'..'z' || c in '0'..'9' || c == '-' || c == '.' || c == '_' || c == '~') {
                sb.append(c)
            } else {
                sb.append('%').append("%02X".format(b.toInt() and 0xFF))
            }
        }
        return sb.toString()
    }
}
