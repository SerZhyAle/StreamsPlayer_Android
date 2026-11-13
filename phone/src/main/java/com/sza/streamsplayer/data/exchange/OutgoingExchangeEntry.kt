package com.sza.streamsplayer.data.exchange

import com.sza.streamsplayer.data.local.db.ExchangeAccountEntryEntity
import com.sza.streamsplayer.data.local.db.ExchangeEntryDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The outgoing entry of `DEVICE-EXCHANGE` 1.6 item DS. This product holds at most one entry (Ask D), so the one
 * recorded entry is the outgoing one the moment it is recorded, with no step by the person, unless its account is
 * a guest's (item AO); removing the entry leaves the device with none (section 1.1 point 7).
 */
@Singleton
class OutgoingExchangeEntry @Inject constructor(entries: ExchangeEntryDao) {

    /** The server a broadcast is announced on, as the person knows it (item BN). */
    data class OutgoingServer(val entryId: String, val name: String)

    /** The outgoing entry, or null while the device holds no entry that may produce. */
    val server: Flow<OutgoingServer?> = entries.observeEntry().map(::of).distinctUntilChanged()

    companion object {
        fun of(entry: ExchangeAccountEntryEntity?): OutgoingServer? {
            val model = entry?.toModel()
            if (model == null || model.isGuest) return null
            return OutgoingServer(model.entryId, model.label?.takeIf(String::isNotBlank) ?: model.endpoint)
        }
    }
}
