package com.sza.streamsplayer.data.exchange

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stores certificate pins for foreign exchange endpoints of imported broadcast channels
 * (DEVICE-EXCHANGE 14.3 item D and PROPOSAL Ask J).
 *
 * Foreign endpoints have no account entry, no device token, and no password. The trust
 * state belongs to (channel, host:port). A leaf that fails the platform store and has
 * no fingerprint in the descriptor is shown to the user and, if accepted, pinned.
 * A changed leaf is refused with both fingerprints.
 */
@Singleton
class ForeignEndpointTrustStore @Inject constructor(
    @ApplicationContext context: Context,
) : BroadcastEndpointTrust {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun getPinnedFingerprint(channelKey: String, hostPort: String): String? {
        val key = makeKey(channelKey, hostPort)
        return prefs.getString(key, null)
    }

    fun pinCertificate(channelKey: String, hostPort: String, fingerprint: String) {
        val key = makeKey(channelKey, hostPort)
        prefs.edit().putString(key, fingerprint).apply()
        Timber.d("S0020: foreign certificate pinned for host=$hostPort")
    }

    fun removePin(channelKey: String, hostPort: String) {
        val key = makeKey(channelKey, hostPort)
        prefs.edit().remove(key).apply()
        Timber.d("S0020: foreign certificate pin removed for host=$hostPort")
    }

    /**
     * Verifies [presentedFingerprint] against any existing pin for `(channelKey, hostPort)`.
     * If a pin is present and does not match, throws [ExchangeCertificateChangedException].
     * If no pin is present, prompts the user via [promptUser] and pins on acceptance.
     */
    override suspend fun verifyOrPrompt(
        channelKey: String,
        hostPort: String,
        presentedFingerprint: String,
        promptUser: suspend (presented: String) -> Boolean,
    ): Boolean {
        val existing = getPinnedFingerprint(channelKey, hostPort)
        if (existing != null) {
            if (existing == presentedFingerprint) {
                return true
            } else {
                Timber.d("S0020: certificate mismatch for host=$hostPort: pinned=$existing presented=$presentedFingerprint")
                throw ExchangeCertificateChangedException(
                    pinnedFingerprint = existing,
                    presentedFingerprint = presentedFingerprint,
                )
            }
        }

        Timber.d("S0020: untrusted certificate presented for host=$hostPort; asking user")
        val accepted = promptUser(presentedFingerprint)
        if (accepted) {
            pinCertificate(channelKey, hostPort, presentedFingerprint)
            return true
        }
        return false
    }

    private fun makeKey(channelKey: String, hostPort: String): String =
        "${channelKey.trim()}_${hostPort.trim().lowercase()}"

    private companion object {
        const val PREFS_NAME = "foreign_endpoint_trust_pins"
    }
}
