package com.sza.streamsplayer.domain.delivery

import kotlinx.coroutines.flow.Flow

/**
 * Source of truth for whether a [DeliverableSet] is available (downloaded), so base code asks this
 * contract instead of touching the payload directly (S0386 Pillar A).
 */
interface DeliverableCapabilityRepository {

    /** Reactive availability state for the offer. */
    fun stateOf(set: DeliverableSet): Flow<DeliverableCapability>

    /**
     * Persist that [set] is installed (survives app update and cache clear). [stamp] identifies which
     * payload was installed, so a later check can tell that the copy on disk is not the current one
     * (S1200).
     */
    suspend fun markInstalled(set: DeliverableSet, stamp: String)

    /**
     * Removes the payload of [set] together with its installed flag and its stamp. The three go together:
     * a stamp that outlives its payload reads as a permanent "update available" (S1200).
     */
    suspend fun uninstall(set: DeliverableSet)

    /**
     * True when [set] has a payload on disk that was installed against a different [expectedStamp].
     *
     * A set that is not installed is not stale; absent is a different question. A payload installed
     * before stamps existed has none recorded and therefore reads as stale: one redundant offer is a
     * better failure than permanently hiding a real update from exactly the people who have the old
     * copy (S1200 §5).
     */
    suspend fun isStale(set: DeliverableSet, expectedStamp: String): Boolean

    /** Synchronous gate for call sites off the main thread: true if the payload is present. */
    fun isInstalledBlocking(set: DeliverableSet): Boolean

    /** Main-safe form of [isInstalledBlocking] for UI call sites: the payload check is a disk stat. */
    suspend fun isInstalled(set: DeliverableSet): Boolean = isInstalledBlocking(set)
}
