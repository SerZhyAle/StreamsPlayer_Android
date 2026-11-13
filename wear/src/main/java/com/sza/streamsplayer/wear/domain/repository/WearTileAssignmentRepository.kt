package com.sza.streamsplayer.wear.domain.repository

import com.sza.streamsplayer.wear.domain.model.WearTileKind
import com.sza.streamsplayer.wear.domain.model.WearTileTargetRef

/**
 * S1955: stores which target each tile kind is assigned to.
 *
 * Stored per kind rather than per tile instance (strategic ADR-3).
 */
interface WearTileAssignmentRepository {
    suspend fun assignmentFor(kind: WearTileKind): WearTileTargetRef?
    suspend fun assign(kind: WearTileKind, ref: WearTileTargetRef)
}
