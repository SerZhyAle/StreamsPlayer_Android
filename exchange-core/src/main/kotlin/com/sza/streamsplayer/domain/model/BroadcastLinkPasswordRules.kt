package com.sza.streamsplayer.domain.model

/**
 * The one place that decides which password set by the person is accepted for a broadcast's outward link
 * (contract LIVE-BROADCAST item R). The rule moves together with the contract, so no caller restates it.
 *
 * No complexity rule exists by the owner's decision; only what breaks the password on the wire is refused.
 * Length is counted in code points, so a supplementary character counts once, and nothing is trimmed - a
 * space the person typed is part of the password.
 */
object BroadcastLinkPasswordRules {

    const val MAX_CODE_POINTS = 64

    // Below this the screen shows a one-line hint; the password is still accepted.
    private const val WEAK_BELOW_CODE_POINTS = 8

    enum class Verdict { OK, EMPTY, TOO_LONG, CONTROL_CHARACTER }

    fun validate(text: String): Verdict = when {
        text.isEmpty() -> Verdict.EMPTY
        text.codePointCount(0, text.length) > MAX_CODE_POINTS -> Verdict.TOO_LONG
        hasControlCharacter(text) -> Verdict.CONTROL_CHARACTER
        else -> Verdict.OK
    }

    fun isWeak(text: String): Boolean = text.codePointCount(0, text.length) < WEAK_BELOW_CODE_POINTS

    // Walks code points by hand rather than String.codePoints(), which needs API 24 on Android.
    private fun hasControlCharacter(text: String): Boolean {
        var index = 0
        while (index < text.length) {
            val codePoint = text.codePointAt(index)
            if (Character.isISOControl(codePoint)) return true
            index += Character.charCount(codePoint)
        }
        return false
    }
}
