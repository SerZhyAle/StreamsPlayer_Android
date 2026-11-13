package com.sza.streamsplayer.data.exchange

/** Redacts stream capability paths even when a playback exception embeds the URL in its stack. */
object BroadcastLogRedactor {
    private val urls = Regex("(?i)\\b(?:https?|rtsp|fmsx)://[^\\s<>\"']+")
    private val invitation = Regex("(?i)([&?]i=)[^&\\s]+")
    private val secrets = Regex("(?i)([\"']?(?:oldPassword|newPassword|password|deviceToken|pairingCode|invitationPassword|invitation)[\"']?\\s*[:=]\\s*)(?:\"[^\"]*\"|'[^']*'|[^,}\\s]+)")
    fun redact(message: String): String {
        val safe = invitation.replace(message) { it.groupValues[1] + "[redacted]" }
        val hidden = secrets.replace(safe) { it.groupValues[1] + "[redacted]" }
        return urls.replace(hidden) { ExchangeTunnelProxy.redactUrl(it.value) }
    }
}
