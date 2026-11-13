package com.sza.streamsplayer.wear.data.wear

import java.time.Instant
import java.time.format.DateTimeParseException
import timber.log.Timber

/** Validation from the donor receiver, kept independent of Android and token storage. */
object WearExchangeHandoverValidation {


        /**
         * Echoes the sender's request id: the phone matches answers by it, so an empty one would be dropped and the
         * person would wait out the whole timeout instead of being told to update the watch app.
         */
        fun unsupportedVersionRefusal(
            parsed: WearExchangeHandoverParseResult.UnsupportedVersion,
        ): WearExchangeHandoverAck {
            Timber.w("Exchange hand-over: unknown format version %d", parsed.version)
            return refusal(parsed.requestId, WearExchangeHandoverReasons.UNSUPPORTED_VERSION)
        }

        private fun refusal(requestId: String, reason: String): WearExchangeHandoverAck {
            Timber.w("Exchange hand-over: refused - %s", reason)
            return WearExchangeHandoverAck(requestId, enrolled = false, reason = reason)
        }

        /**
         * True only for a server time already past. A text this build cannot read is left to the server, which
         * refuses a stale code with its own reason - refusing it here would turn a format change into an outage.
         */
        fun isExpired(expiresAt: String?, now: Instant): Boolean = try {
            expiresAt != null && Instant.parse(expiresAt).isBefore(now)
        } catch (e: DateTimeParseException) {
            Timber.w("Exchange hand-over: unreadable expiry (%s)", e.javaClass.simpleName)
            false
        }
}
