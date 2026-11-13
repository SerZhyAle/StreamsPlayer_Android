package com.sza.streamsplayer.data.exchange.directory

import kotlinx.coroutines.flow.Flow

/** What an observation of the account directory reports (DEVICE-EXCHANGE 7.4). */
sealed interface DirectoryUpdate {
    /** The directory as it stands now: a `directory` answer or a `changed` delta already applied. */
    data class Snapshot(val directory: DirectorySnapshot) : DirectoryUpdate

    /** The control stream closed. The last snapshot is not known to be wrong, only no longer followed. */
    data object Disconnected : DirectoryUpdate
}

/** The seam between the shared directory observer and the control stream, so the observer runs on a fake. */
interface DirectorySource {
    /** Emits until cancelled; throws `DeviceExchangeRefusedException` when the server refuses. */
    fun observe(): Flow<DirectoryUpdate>

    /** Asks for a fresh `list` on the held stream; false when no stream is held or the send failed. */
    suspend fun requestList(): Boolean
}
