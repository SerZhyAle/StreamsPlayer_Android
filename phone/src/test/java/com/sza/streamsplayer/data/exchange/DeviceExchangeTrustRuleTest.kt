package com.sza.streamsplayer.data.exchange

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Trust rule of contract DEVICE-EXCHANGE 14.3 item D and amendment V:
 * - platform trust store for the host name first (nothing recorded)
 * - leaf that fails it is UNTRUSTED (NeedsDecision) and is pinned only after user accepts its fingerprint
 * - pin that no longer matches is CHANGED (Refused(expected, actual))
 * - fp from connection string is the anchor: leaf must equal it without prompt; mismatch is refused
 */
class DeviceExchangeTrustRuleTest {

    @Test
    fun `renewed pinned leaf with the same public key is accepted without platform trust`() {
        assertEquals(ExchangeTrustDecision.Verdict.ACCEPT, ExchangeTrustDecision.decide(
            ExchangeTrust.Verified("SHA256:old-leaf", "SHA256:accepted-key"), false,
            "SHA256:new-leaf", "SHA256:accepted-key"))
    }

    @Test
    fun `a changed key is refused even if the platform trusts its certificate`() {
        assertEquals(ExchangeTrustDecision.Verdict.CHANGED, ExchangeTrustDecision.decide(
            ExchangeTrust.Verified("SHA256:old-leaf", "SHA256:accepted-key"), true,
            "SHA256:new-leaf", "SHA256:different-key"))
    }

    @Test
    fun `legacy leaf pin cannot silently acquire another key on renewal`() {
        assertEquals(ExchangeTrustDecision.Verdict.CHANGED, ExchangeTrustDecision.decide(
            ExchangeTrust.Verified("SHA256:old-leaf"), false, "SHA256:new-leaf", "SHA256:key"))
    }

    private val leafFingerprintA = "SHA256:8f6TQvCbXjDMOyu4A9JzKcWlEHmR5pNsGgVaU2wYqhk"
    private val leafFingerprintB = "SHA256:4aB1CvDbXjDMOyu4A9JzKcWlEHmR5pNsGgVaU2wYzzz"

    @Test
    fun `platform-valid leaf with no pin is accepted without recording a pin`() {
        val trust = ExchangeTrust.Verified(pinned = null)
        val verdict = ExchangeTrustDecision.decide(trust, platformTrusted = true, presented = leafFingerprintA)
        assertEquals(ExchangeTrustDecision.Verdict.ACCEPT, verdict)
    }

    @Test
    fun `self-signed leaf with no pin is untrusted and requires user decision`() {
        val trust = ExchangeTrust.Verified(pinned = null)
        val verdict = ExchangeTrustDecision.decide(trust, platformTrusted = false, presented = leafFingerprintA)
        assertEquals(ExchangeTrustDecision.Verdict.UNTRUSTED, verdict)
    }

    @Test
    fun `self-signed leaf matching pinned fingerprint is accepted`() {
        val trust = ExchangeTrust.Verified(pinned = leafFingerprintA)
        val verdict = ExchangeTrustDecision.decide(trust, platformTrusted = false, presented = leafFingerprintA)
        assertEquals(ExchangeTrustDecision.Verdict.ACCEPT, verdict)
    }

    @Test
    fun `changed leaf differing from pinned fingerprint is refused`() {
        val trust = ExchangeTrust.Verified(pinned = leafFingerprintA)
        val verdict = ExchangeTrustDecision.decide(trust, platformTrusted = false, presented = leafFingerprintB)
        assertEquals(ExchangeTrustDecision.Verdict.CHANGED, verdict)
    }

    @Test
    fun `fp from connection string anchors trust - equal is accepted`() {
        // fp acts as the anchor pin
        val trust = ExchangeTrust.Verified(pinned = leafFingerprintA)
        val verdict = ExchangeTrustDecision.decide(trust, platformTrusted = false, presented = leafFingerprintA)
        assertEquals(ExchangeTrustDecision.Verdict.ACCEPT, verdict)
    }

    @Test
    fun `fp from connection string anchors trust - differing is refused`() {
        val trust = ExchangeTrust.Verified(pinned = leafFingerprintA)
        val verdict = ExchangeTrustDecision.decide(trust, platformTrusted = false, presented = leafFingerprintB)
        assertEquals(ExchangeTrustDecision.Verdict.CHANGED, verdict)
    }
}
