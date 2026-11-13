package com.sza.streamsplayer.data.exchange

import java.io.InputStream

/**
 * Pure Kotlin codec for the DEVICE-EXCHANGE FMSEXCH1 connection string.
 *
 * Implements:
 * - PROPOSAL-2026-10-10-exchange-connection-string.md (Ask 1: prefix, fields, file, QR, compatibility)
 * - PROPOSAL-2026-10-10-connection-string-grammar-and-handling.md (Grammar points 1-14)
 * - DEVICE-EXCHANGE 5.1 (login), 7.1 (SHA256: fingerprint), 7.2 (publicEndpoint)
 */
data class ExchangeConnectionString(
    val hostPort: String,
    val fingerprint: String? = null,
    val name: String? = null,
    val login: String? = null
) {

    enum class Rule {
        PREFIX_CASE_SENSITIVE,
        MAX_LENGTH_512,
        NO_INTERNAL_WHITESPACE,
        FIELD_SYNTAX,
        NO_DUPLICATE_KNOWN_KEYS,
        EMPTY_KNOWN_VALUE,
        PERCENT_ENCODING,
        HOST_PORT_REQUIRED,
        HOST_SYNTAX,
        PORT_RANGE,
        FINGERPRINT_SYNTAX,
        NAME_SYNTAX,
        LOGIN_SYNTAX,
        INVITATION_SYNTAX,
        FILE_TOO_LARGE,
        FILE_ENCODING,
        FILE_MULTIPLE_LINES
    }

    object Rules {
        /** Prefix FMSEXCH1: case-sensitive (Proposal Ask 1, Grammar point 11). */
        const val PREFIX_CASE_SENSITIVE = "RULE_PREFIX_CASE_SENSITIVE"

        /** At most 512 characters on the trimmed text (Proposal Ask 1, Grammar point 1). */
        const val MAX_LENGTH_512 = "RULE_MAX_LENGTH_512"

        /** White space inside refused (Proposal Ask 1, Grammar point 1). */
        const val NO_INTERNAL_WHITESPACE = "RULE_NO_INTERNAL_WHITESPACE"

        /** &-separated key=value; no empty fields, no field without = (Proposal Ask 1, Grammar point 4). */
        const val FIELD_SYNTAX = "RULE_FIELD_SYNTAX"

        /** Known key repeated refused (Proposal Ask 1, Grammar point 4). */
        const val NO_DUPLICATE_KNOWN_KEYS = "RULE_NO_DUPLICATE_KNOWN_KEYS"

        /** Empty value of a known key breaks its rule and refuses (Grammar point 4). */
        const val EMPTY_KNOWN_VALUE = "RULE_EMPTY_KNOWN_VALUE"

        /** Strict UTF-8 percent decoding with + as space; raw non-ASCII refused (Grammar point 5). */
        const val PERCENT_ENCODING = "RULE_PERCENT_ENCODING"

        /** x host:port is required (Proposal Ask 1). */
        const val HOST_PORT_REQUIRED = "RULE_HOST_PORT_REQUIRED"

        /** Host syntax: DNS name, IPv4 dotted quad, or bracketed IPv6 (Grammar point 2). */
        const val HOST_SYNTAX = "RULE_HOST_SYNTAX"

        /** Port 1-65535, ASCII digits, leading zero tolerated (Grammar point 3). */
        const val PORT_RANGE = "RULE_PORT_RANGE"

        /** fp SHA256: plus 43 standard base64 chars without padding or stray bits (Grammar point 6). */
        const val FINGERPRINT_SYNTAX = "RULE_FINGERPRINT_SYNTAX"

        /** n at most 64 scalar values, no control characters (Grammar point 1). */
        const val NAME_SYNTAX = "RULE_NAME_SYNTAX"

        /** u login hint form of 5.1: 3-64 chars of [a-z0-9._-] (Grammar point 7). */
        const val LOGIN_SYNTAX = "RULE_LOGIN_SYNTAX"
        const val INVITATION_SYNTAX = "RULE_INVITATION_SYNTAX"

        /** File reader: at most 4096 bytes (Proposal Ask 1, Grammar point 9). */
        const val FILE_TOO_LARGE = "RULE_FILE_TOO_LARGE"

        /** File reader: strict UTF-8 (Grammar point 9). */
        const val FILE_ENCODING = "RULE_FILE_ENCODING"

        /** File reader: second non-empty line refused (Grammar point 9). */
        const val FILE_MULTIPLE_LINES = "RULE_FILE_MULTIPLE_LINES"
    }

    sealed interface Outcome {
        data class Valid(
            val hostPort: String,
            val fingerprint: String? = null,
            val name: String? = null,
            val login: String? = null,
            val isInvitation: Boolean = false,
        ) : Outcome {
            val connectionString: ExchangeConnectionString
                get() = ExchangeConnectionString(hostPort, fingerprint, name, login)
        }

        data class UnsupportedMajor(val prefix: String) : Outcome

        sealed interface NotExchange : Outcome {
            val candidate: String?

            companion object : NotExchange {
                override val candidate: String? = null
                operator fun invoke(candidate: String? = null): NotExchange =
                    if (candidate == null) this else Candidate(candidate)
            }

            private data class Candidate(override val candidate: String) : NotExchange {
                override fun toString(): String = "NotExchange(candidate=$candidate)"
            }
        }

        data class Refused(val rule: String) : Outcome {
            constructor(rule: Rule) : this(rule.name)
        }
    }

    companion object {
        const val MIME_TYPE_FILE = "application/x-fmsexch"
        const val MIME_TYPE_TEXT = "text/plain"
        const val FILE_EXTENSION = "fmsexch"
        const val PREFIX = "FMSEXCH1:"
        const val MAX_LENGTH = 512
        const val MAX_NAME_CODE_POINTS = 64
        const val MAX_FILE_BYTES = 4096

        private val OTHER_FAMILY = Regex("^(FMSSFTP[0-9]+|FMSBCAST1):")

        fun parse(text: String): Outcome = classify(
            com.sza.streamsplayer.domain.model.ExchangeConnectionStringCodec.decode(text), text
        )

        /**
         * The file route recognises another family's text exactly as the pasted route does (Ask 1: one reading for
         * a file, a paste, a QR payload and a share intent); the lenient decoding only feeds that recognition, the
         * codec's strict reading of the same bytes decides the outcome.
         */
        fun readConnectionStringFile(stream: InputStream): Outcome {
            val bytes = com.sza.streamsplayer.domain.model.ExchangeConnectionStringCodec.readFileBytes(stream)
            return classify(
                com.sza.streamsplayer.domain.model.ExchangeConnectionStringCodec.decodeFile(bytes),
                bytes.toString(Charsets.UTF_8)
            )
        }

        private fun classify(
            result: com.sza.streamsplayer.domain.model.ExchangeConnectionStringResult,
            text: String
        ): Outcome {
            if (result is com.sza.streamsplayer.domain.model.ExchangeConnectionStringResult.Refused &&
                result.reason == com.sza.streamsplayer.domain.model.ExchangeConnectionStringRefusal.NOT_A_STRING) {
                val trimmed = text.removePrefix("\uFEFF").trim()
                val family = OTHER_FAMILY.find(trimmed)?.groupValues?.get(1)
                if (family != null) return Outcome.NotExchange(family)
                if (trimmed.startsWith("{") && listOf("schemaVersion", "stream", "broadcast", "url").any {
                        trimmed.contains("\"$it\"")
                    }) return Outcome.NotExchange("broadcast descriptor")
                if (result.looks == com.sza.streamsplayer.domain.model.ExchangeConnectionStringLookalike.URL) {
                    return Outcome.NotExchange("URL")
                }
            }
            return adapt(result)
        }

        fun encode(value: ExchangeConnectionString): String =
            com.sza.streamsplayer.domain.model.ExchangeConnectionStringCodec.encode(
                com.sza.streamsplayer.domain.model.ExchangeConnectionString(
                    endpoint = requireNotNull(com.sza.streamsplayer.domain.model.ExchangeEndpoint.parse(value.hostPort)),
                    fingerprint = value.fingerprint,
                    name = value.name,
                    loginHint = value.login?.lowercase(java.util.Locale.ROOT),
                )
            )

        fun encode(valid: Outcome.Valid): String = encode(valid.connectionString)
        fun encode(outcome: Outcome): String = encode((outcome as Outcome.Valid).connectionString)

        private fun adapt(result: com.sza.streamsplayer.domain.model.ExchangeConnectionStringResult): Outcome = when (result) {
            is com.sza.streamsplayer.domain.model.ExchangeConnectionStringResult.Read -> result.value.let {
                // A sign-in-only client validates the invitation but never retains or forwards its secret.
                Outcome.Valid(it.endpoint.toString(), it.fingerprint, it.name, it.loginHint, it.isInvitation)
            }
            is com.sza.streamsplayer.domain.model.ExchangeConnectionStringResult.NewerMajor ->
                Outcome.UnsupportedMajor("FMSEXCH${result.major}:")
            is com.sza.streamsplayer.domain.model.ExchangeConnectionStringResult.Refused -> {
                val rule = when (result.reason) {
                    com.sza.streamsplayer.domain.model.ExchangeConnectionStringRefusal.NOT_A_STRING -> Rules.PREFIX_CASE_SENSITIVE
                    com.sza.streamsplayer.domain.model.ExchangeConnectionStringRefusal.TOO_LONG -> Rules.MAX_LENGTH_512
                    com.sza.streamsplayer.domain.model.ExchangeConnectionStringRefusal.WHITESPACE -> Rules.NO_INTERNAL_WHITESPACE
                    com.sza.streamsplayer.domain.model.ExchangeConnectionStringRefusal.MALFORMED_FIELD -> Rules.FIELD_SYNTAX
                    com.sza.streamsplayer.domain.model.ExchangeConnectionStringRefusal.REPEATED_KEY -> Rules.NO_DUPLICATE_KNOWN_KEYS
                    com.sza.streamsplayer.domain.model.ExchangeConnectionStringRefusal.MISSING_X -> Rules.HOST_PORT_REQUIRED
                    com.sza.streamsplayer.domain.model.ExchangeConnectionStringRefusal.BAD_X -> Rules.HOST_SYNTAX
                    com.sza.streamsplayer.domain.model.ExchangeConnectionStringRefusal.BAD_FP -> Rules.FINGERPRINT_SYNTAX
                    com.sza.streamsplayer.domain.model.ExchangeConnectionStringRefusal.BAD_N -> Rules.NAME_SYNTAX
                    com.sza.streamsplayer.domain.model.ExchangeConnectionStringRefusal.BAD_U -> Rules.LOGIN_SYNTAX
                    com.sza.streamsplayer.domain.model.ExchangeConnectionStringRefusal.BAD_I -> Rules.INVITATION_SYNTAX
                    com.sza.streamsplayer.domain.model.ExchangeConnectionStringRefusal.FILE_TOO_LONG -> Rules.FILE_TOO_LARGE
                    com.sza.streamsplayer.domain.model.ExchangeConnectionStringRefusal.FILE_LINES -> Rules.FILE_MULTIPLE_LINES
                    else -> Rules.PERCENT_ENCODING
                }
                if (result.looks != null) Outcome.NotExchange(result.looks.code) else Outcome.Refused(rule)
            }
        }
    }
}

/**
 * Pure Kotlin default message for TalkBack / error presentation.
 */
fun ExchangeConnectionString.Outcome.errorMessage(): String? = when (this) {
    is ExchangeConnectionString.Outcome.Valid -> null
    is ExchangeConnectionString.Outcome.UnsupportedMajor ->
        "Unsupported DEVICE-EXCHANGE version ($prefix). Please update the app."
    is ExchangeConnectionString.Outcome.NotExchange -> if (candidate != null) {
        "This is not an exchange server connection string (looks like $candidate)."
    } else {
        "This is not a valid connection string."
    }
    is ExchangeConnectionString.Outcome.Refused -> "This is not a valid connection string."
}


