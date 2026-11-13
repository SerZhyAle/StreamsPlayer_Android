package com.sza.streamsplayer.domain.model

/** Why sealed content that came through the server is not usable here (DEVICE-EXCHANGE 27 items DP and DR). */
enum class ExchangeSealIssue(val message: ExchangeMessage) {
    /** The box did not open: not addressed here, an untrusted sender, a failed tag, a moved context. */
    SEALED_FAILED(ExchangeMessage.SEALED_FAILED),

    /** This device holds no key of its own, so a box addressed to the entry cannot open here. */
    E2E_MISSING(ExchangeMessage.E2E_MISSING),

    /** A grant's box came from another key than its first one (27 item DJ). */
    KEY_CHANGED(ExchangeMessage.KEY_CHANGED),
}
