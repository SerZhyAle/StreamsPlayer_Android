package com.sza.streamsplayer.data.exchange.directory

import com.google.gson.JsonParser
import com.sza.streamsplayer.data.exchange.publish.BroadcastTunnelAcceptor
import com.sza.streamsplayer.data.security.e2e.E2eBroadcastSeals
import com.sza.streamsplayer.data.security.e2e.E2eBytes
import com.sza.streamsplayer.data.security.e2e.E2eOpenFailure
import com.sza.streamsplayer.data.security.e2e.E2eOpenResult
import com.sza.streamsplayer.data.security.e2e.E2eSealedBox
import com.sza.streamsplayer.data.security.e2e.E2eSelf
import com.sza.streamsplayer.domain.model.ExchangeSealIssue
import com.sza.streamsplayer.data.exchange.ExchangeLog as Timber

/**
 * Opens the boxes of the broadcast records an entry's server delivered (contract DEVICE-EXCHANGE 27 items DH, DJ and
 * DK), so every later step - the list, the keep, the player - reads the keys from the endpoints' `seal` exactly as
 * in a descriptor a person handed over, and the own pair of a protected broadcast from its `credentials` (item BF). Ported from the broadcast half of FastMediaSorter's
 * `ExchangeSealedContentOpener`; this product serves no folder, so the `access` half is not carried.
 *
 * The opened keys live in memory only, on the records this returns.
 */
class ExchangeBroadcastOpener {

    /** The device that opens: [self] is null for an entry enrolled without a key, which opens nothing. */
    class Sealing(val self: E2eSelf?, private val trustCheck: (String, ByteArray) -> Boolean) {
        fun trusts(from: String, fromKey: ByteArray): Boolean = trustCheck(from, fromKey)
    }

    /**
     * The first sender key of each grant a guest has seen (27 item DJ). [learned] collects the keys of boxes that
     * opened for the first time, kept by the caller only after the whole directory was read.
     */
    class GrantTrust(private val held: Map<String, String>) {
        val learned = mutableMapOf<String, String>()

        fun knows(grantId: String, fromKey: ByteArray): Boolean? =
            (held[grantId] ?: learned[grantId])?.let { it == E2eBytes.b64url(fromKey) }

        fun learn(grantId: String, fromKey: ByteArray) {
            if (held[grantId] == null) learned.putIfAbsent(grantId, E2eBytes.b64url(fromKey))
        }
    }

    // The last outcome logged per broadcast, so a re-opened directory does not repeat itself.
    private val reported = HashMap<String, String>()

    /**
     * Opens every broadcast of [broadcasts]. Item DJ: a granted record whose box is the registering one is not listed
     * until its replacing box arrives; a device with no key of its own still lists it, so it can show why nothing
     * will open.
     */
    fun open(
        sealing: Sealing,
        broadcasts: List<DeviceExchangeBroadcastRecord>,
        grants: GrantTrust,
    ): List<DeviceExchangeBroadcastRecord> {
        val registering = { record: DeviceExchangeBroadcastRecord ->
            sealing.self != null && record.grantId != null &&
                E2eSealedBox.addressesNobody(record.sealed?.let { runCatching { JsonParser.parseString(it) }.getOrNull() })
        }
        val (hidden, shown) = broadcasts.partition(registering)
        val openings = shown.map { opening(sealing, it, grants) }
        val listed = broadcasts.mapTo(HashSet()) { it.broadcastId }
        synchronized(reported) { reported.keys.retainAll(listed) }
        report(hidden.map { it to OUTCOME_HIDDEN_REGISTERING } + openings.map { it.source to it.outcome })
        return openings.map { it.record }
    }

    /** A broadcast offered by `cast` (7.8), opened with the keys this device already trusts. */
    fun openBroadcast(sealing: Sealing, record: DeviceExchangeBroadcastRecord): DeviceExchangeBroadcastRecord =
        opening(sealing, record, GrantTrust(emptyMap())).also { report(listOf(it.source to it.outcome)) }.record

    private class Opening(
        val source: DeviceExchangeBroadcastRecord,
        val record: DeviceExchangeBroadcastRecord,
        val outcome: String,
    )

    private fun opening(
        sealing: Sealing,
        record: DeviceExchangeBroadcastRecord,
        grants: GrantTrust,
    ): Opening {
        val box = record.sealed?.let { runCatching { JsonParser.parseString(it) }.getOrNull() }
        val self = sealing.self
        val grantId = record.grantId
        // Item DJ: a granted broadcast's box is the grant's, opened with the first sender key of that grant.
        val result = when {
            box == null || self == null -> null
            grantId != null -> E2eSealedBox.open(box, E2eSealedBox.grantContext(grantId), self) { _, fromKey ->
                grants.knows(grantId, fromKey) != false
            }
            else -> E2eSealedBox.open(box, E2eSealedBox.broadcastContext(record.broadcastId), self, sealing::trusts)
        }
        val opened = result as? E2eOpenResult.Opened
        if (grantId != null) opened?.let { grants.learn(grantId, it.fromKey) }
        val issue = brokenBoxIssue(record, opened, result, keyless = self == null)
        val descriptor = runCatching { JsonParser.parseString(record.descriptorJson).asJsonObject }.getOrNull()
        // Item DK: the keys go into the endpoints' `seal`; a box that does not open leaves the sealed endpoints
        // keyless, and a keyless sealed endpoint is never played.
        val unsealed = if (descriptor == null) {
            record.copy(sealed = null, sealIssue = issue)
        } else {
            E2eBroadcastSeals.withKeys(descriptor, opened?.payload)
            // Item BF: the pair rides on the opened descriptor, in memory only, so the broadcast asks nothing - the
            // own pair for an own broadcast, the grant's pair for a guest (BD).
            opened?.payload?.get(E2eBroadcastSeals.KEY_CREDENTIALS)?.takeIf { it.isJsonObject }
                ?.let { descriptor.add(E2eBroadcastSeals.KEY_CREDENTIALS, it) }
            record.copy(descriptorJson = descriptor.toString(), sealed = null, sealIssue = issue)
        }
        return Opening(record, unsealed, outcomeOf(record.sealed != null, self, result))
    }

    // A granted record always names its reason (item DJ). An own-account record names one only when it came sealed
    // and the box did not open, so a record delivered without a box stays as it was.
    private fun brokenBoxIssue(
        record: DeviceExchangeBroadcastRecord,
        opened: E2eOpenResult.Opened?,
        result: E2eOpenResult?,
        keyless: Boolean,
    ): ExchangeSealIssue? = when {
        opened != null -> null
        record.grantId != null -> grantIssue(result)
        record.sealed == null -> null
        keyless -> ExchangeSealIssue.E2E_MISSING
        else -> ExchangeSealIssue.SEALED_FAILED
    }

    private fun grantIssue(result: E2eOpenResult?): ExchangeSealIssue =
        if (result == E2eOpenResult.Failed(E2eOpenFailure.UNTRUSTED_SENDER)) {
            ExchangeSealIssue.KEY_CHANGED
        } else {
            ExchangeSealIssue.SEALED_FAILED
        }

    private fun outcomeOf(hasBox: Boolean, self: E2eSelf?, result: E2eOpenResult?): String = when {
        !hasBox -> OUTCOME_NO_BOX
        self == null -> OUTCOME_NO_KEY
        result is E2eOpenResult.Opened -> OUTCOME_OPENED
        result is E2eOpenResult.Failed -> failureOutcome(result.reason)
        else -> OUTCOME_FAILED_PREFIX + E2eOpenFailure.MALFORMED.name.lowercase()
    }

    private fun failureOutcome(reason: E2eOpenFailure): String = when (reason) {
        E2eOpenFailure.NO_ENTRY -> OUTCOME_NOT_ADDRESSED
        E2eOpenFailure.UNTRUSTED_SENDER -> OUTCOME_UNTRUSTED_SENDER
        else -> OUTCOME_FAILED_PREFIX + reason.name.lowercase()
    }

    // A record logs again only when its outcome changed or it left and came back; the id only by its hash (7.9).
    private fun report(outcomes: List<Pair<DeviceExchangeBroadcastRecord, String>>) {
        val fresh = synchronized(reported) {
            outcomes.filter { (record, outcome) -> reported.put(record.broadcastId, outcome) != outcome }
        }
        fresh.forEach { (record, outcome) ->
            Timber.d("Exchange broadcast opened id=${BroadcastTunnelAcceptor.hashId(record.broadcastId)} outcome=$outcome")
        }
    }

    private companion object {
        const val OUTCOME_OPENED = "opened"
        const val OUTCOME_NO_BOX = "no-box"
        const val OUTCOME_NO_KEY = "no-device-key"
        const val OUTCOME_NOT_ADDRESSED = "not-addressed"
        const val OUTCOME_UNTRUSTED_SENDER = "untrusted-sender"
        const val OUTCOME_FAILED_PREFIX = "failed-"
        const val OUTCOME_HIDDEN_REGISTERING = "hidden-registering-box"
    }
}
