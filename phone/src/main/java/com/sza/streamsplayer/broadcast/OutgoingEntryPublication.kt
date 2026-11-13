package com.sza.streamsplayer.broadcast

import com.sza.streamsplayer.data.exchange.OutgoingExchangeEntry
import com.sza.streamsplayer.data.exchange.publish.BroadcastExchangePublisher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * `DEVICE-EXCHANGE` 1.6 item DT on the phone: a live broadcast is announced on the outgoing entry from its start,
 * with no switch, and for as long as it runs. An entry recorded while the broadcast is live announces it at once;
 * the entry removed, or replaced by another, ends it there (`broadcast-end`) and the next one announces it again.
 * A device with no entry that may produce broadcasts on its own network only.
 */
internal class OutgoingEntryPublication(
    private val scope: CoroutineScope,
    outgoing: Flow<OutgoingExchangeEntry.OutgoingServer?>,
    private val publisher: BroadcastExchangePublisher,
) {
    private val entryIds = outgoing.map { it?.entryId }.distinctUntilChanged()
    private var job: Job? = null

    /**
     * Follows the outgoing entry for the session [session] builds; [onAnnounce] runs before each announcement and
     * [onWithdraw] after each end, so a feed of the relay upload runs only while there is a server to take it.
     */
    fun bind(
        session: () -> BroadcastExchangePublisher.Session,
        onAnnounce: () -> Unit = {},
        onWithdraw: () -> Unit = {},
    ) {
        job?.cancel()
        job = scope.launch {
            var announced = false
            entryIds.collectLatest { entryId ->
                if (announced) {
                    publisher.endAndAwait()
                    onWithdraw()
                    announced = false
                }
                Timber.d("S0021: outgoing entry %s, the broadcast follows it", if (entryId != null) "present" else "absent")
                if (entryId != null) {
                    announced = true
                    onAnnounce()
                    publisher.startAndAwait(session())
                }
            }
        }
    }

    /** Stops following; the caller's stop path ends the publish itself. */
    fun unbind() {
        job?.cancel()
        job = null
    }
}
