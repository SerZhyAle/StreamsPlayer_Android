package com.sza.streamsplayer.data.exchange

import org.junit.Assert.*
import org.junit.Test

class BroadcastLogRedactorTest {

    @org.junit.Test
    fun `account secrets and invitation strings are redacted`() {
        val canary = "CANARY_ACCOUNT_SECRET_27"
        val inputs = listOf(
            "FMSEXCH1:x=example.net%3A443&u=alice&i=$canary",
            "{\"oldPassword\":\"$canary\",\"newPassword\":\"$canary\"}",
            "password=$canary deviceToken=$canary pairingCode=$canary",
            "invitationPassword=$canary invitation=$canary",
        )
        inputs.forEach { org.junit.Assert.assertFalse(BroadcastLogRedactor.redact(it).contains(canary)) }
    }
    @Test fun `capabilities credentials and queries are removed from log and exception text`() {
        val message = "stream https://user:password@relay.example:8443/listen/capability?token=secret\n" +
            "Caused by: failed fmsx://server:443/b/broadcast-id/http and rtsp://lan/live/path"
        val redacted = BroadcastLogRedactor.redact(message)
        for (secret in listOf("password", "capability", "secret", "broadcast-id", "/live/path")) {
            assertFalse(redacted.contains(secret))
        }
        assertTrue(redacted.contains("relay.example:8443"))
        assertTrue(redacted.contains("Caused by:"))
    }
}
