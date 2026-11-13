package com.sza.streamsplayer.data.exchange

import android.content.Context
import com.sza.streamsplayer.R

/**
 * Formats [ExchangeConnectionString.Outcome] into localized, TalkBack-safe user messages.
 */
fun ExchangeConnectionString.Outcome.formatUserMessage(context: Context): String? = when (this) {
    is ExchangeConnectionString.Outcome.Valid -> null
    is ExchangeConnectionString.Outcome.UnsupportedMajor ->
        context.getString(R.string.exchange_connection_unsupported_major, prefix)
    is ExchangeConnectionString.Outcome.NotExchange -> {
        val label = when (candidate) {
            "FMSSFTP1" -> context.getString(R.string.exchange_connection_candidate_sftp1)
            "FMSSFTP2" -> context.getString(R.string.exchange_connection_candidate_sftp2)
            "broadcast descriptor" -> context.getString(R.string.exchange_connection_candidate_broadcast)
            "URL" -> context.getString(R.string.exchange_connection_candidate_url)
            else -> candidate
        }
        if (label != null) {
            context.getString(R.string.exchange_connection_not_exchange, label)
        } else {
            context.getString(R.string.exchange_connection_invalid)
        }
    }
    is ExchangeConnectionString.Outcome.Refused ->
        context.getString(R.string.exchange_connection_invalid)
}
