package com.sza.streamsplayer.data.delivery

import com.sza.streamsplayer.domain.delivery.DeliverableCapability
import com.sza.streamsplayer.domain.delivery.DeliverableCapabilityRepository
import com.sza.streamsplayer.domain.delivery.DeliverableSet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Answers from the downloaded-payload state whether a [DeliverableSet] is usable (S0386 Pillar A).
 */
@Singleton
class DeliverableCapabilityRepositoryImpl @Inject constructor(
    private val markerStore: InstalledSetMarkerStore
) : DeliverableCapabilityRepository {

    override fun stateOf(set: DeliverableSet): Flow<DeliverableCapability> =
        markerStore.installedFlagFlow(set).map {
            // The collector may be the main thread (the atlas prompt), and the payload check is a stat.
            if (isInstalled(set)) {
                DeliverableCapability.INSTALLED
            } else {
                DeliverableCapability.NOT_INSTALLED
            }
        }

    override suspend fun markInstalled(set: DeliverableSet, stamp: String) {
        // S1200: flag and stamp are written together, from the one place that knows an install just
        // succeeded. Splitting them would let the two disagree, and a stamp that outlives its payload
        // reads as a permanent "update available".
        markerStore.setInstalled(set, true)
        markerStore.setStamp(set, stamp)
    }

    override suspend fun uninstall(set: DeliverableSet) {
        // The three steps run to the end even if the caller goes away between them (a Remove tap followed by
        // leaving the screen): a stamp left behind without its payload is the S1200 hazard. The recursive
        // delete is disk work, and the caller may sit on the main dispatcher.
        withContext(NonCancellable) {
            withContext(Dispatchers.IO) { markerStore.deletePayload(set) }
            markerStore.setInstalled(set, false)
            markerStore.clearStamp(set)
        }
    }

    override suspend fun isStale(set: DeliverableSet, expectedStamp: String): Boolean {
        if (!withContext(Dispatchers.IO) { markerStore.isPayloadPresent(set) }) return false
        return markerStore.installedStamp(set) != expectedStamp
    }

    override fun isInstalledBlocking(set: DeliverableSet): Boolean =
        markerStore.isPayloadPresent(set)

    override suspend fun isInstalled(set: DeliverableSet): Boolean =
        withContext(Dispatchers.IO) { markerStore.isPayloadPresent(set) }
}
