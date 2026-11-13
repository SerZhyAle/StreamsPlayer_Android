package com.sza.streamsplayer.ui.exchange

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.sza.streamsplayer.R
import com.sza.streamsplayer.data.exchange.ExchangeConnectionString
import com.sza.streamsplayer.data.exchange.formatUserMessage
import timber.log.Timber
import java.io.ByteArrayInputStream
import java.io.InputStream

/**
 * Thin exported activity handling VIEW (.fmsexch) and SEND (text/plain) intents (S0019 Scope 5).
 * Hostile-input surface: reads through bounded reader (max 4 KiB, strict UTF-8, single line).
 * On valid input, fills the sign-in form and stops.
 * On invalid input, shows one message and returns without connection.
 * Excluded from recents, returns no result to the caller.
 */
class ExchangeImportActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Timber.d("S0019: ExchangeImportActivity handling action=${intent?.action}")
        setResult(Activity.RESULT_CANCELED)

        try {
            processIntent(intent)
        } catch (e: Exception) {
            Timber.d(e, "S0019: error handling import intent")
            showError(getString(R.string.exchange_connection_invalid))
        } finally {
            finish()
        }
    }

    private fun processIntent(intent: Intent?) {
        if (intent == null) return

        val outcome: ExchangeConnectionString.Outcome = when (intent.action) {
            Intent.ACTION_VIEW -> {
                val dataUri = intent.data
                if (dataUri != null) {
                    readStreamFromUri(dataUri)
                } else {
                    ExchangeConnectionString.Outcome.Refused(ExchangeConnectionString.Rules.PREFIX_CASE_SENSITIVE)
                }
            }
            Intent.ACTION_SEND -> {
                @Suppress("DEPRECATION")
                val extraStream = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                if (extraStream != null) {
                    readStreamFromUri(extraStream)
                } else {
                    val text = intent.getStringExtra(Intent.EXTRA_TEXT)
                    if (text != null) {
                        val bytes = text.toByteArray(Charsets.UTF_8)
                        val stream = ByteArrayInputStream(bytes)
                        ExchangeConnectionString.readConnectionStringFile(stream)
                    } else {
                        ExchangeConnectionString.Outcome.Refused(ExchangeConnectionString.Rules.PREFIX_CASE_SENSITIVE)
                    }
                }
            }
            else -> {
                ExchangeConnectionString.Outcome.Refused(ExchangeConnectionString.Rules.PREFIX_CASE_SENSITIVE)
            }
        }

        when (outcome) {
            is ExchangeConnectionString.Outcome.Valid -> {
                Timber.d("S0019: valid connection string extracted, forwarding to form")
                val targetIntent = Intent(this, ExchangeActivity::class.java).apply {
                    putExtra(ExchangeActivity.EXTRA_CONNECTION_STRING, ExchangeConnectionString.encode(outcome.connectionString))
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                startActivity(targetIntent)
            }
            is ExchangeConnectionString.Outcome.UnsupportedMajor,
            is ExchangeConnectionString.Outcome.NotExchange,
            is ExchangeConnectionString.Outcome.Refused -> {
                val message = outcome.formatUserMessage(this) ?: getString(R.string.exchange_connection_invalid)
                Timber.d("S0019: import refused with message")
                showError(message)
            }
        }
    }

    private fun readStreamFromUri(uri: Uri): ExchangeConnectionString.Outcome {
        return try {
            contentResolver.openInputStream(uri)?.use { stream ->
                ExchangeConnectionString.readConnectionStringFile(stream)
            } ?: ExchangeConnectionString.Outcome.Refused(ExchangeConnectionString.Rules.FILE_ENCODING)
        } catch (e: Exception) {
            Timber.d(e, "S0019: exception reading uri stream")
            ExchangeConnectionString.Outcome.Refused(ExchangeConnectionString.Rules.FILE_ENCODING)
        }
    }

    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
