package com.sza.streamsplayer.wear.tile

import com.sza.streamsplayer.wear.domain.model.WearTileKind
import dagger.hilt.android.AndroidEntryPoint

/**
 * S1955: Wear OS TileService for displaying assigned Stream tile.
 */
@AndroidEntryPoint
class WearStreamTileService : BaseWearTileService() {
    override val kind: WearTileKind = WearTileKind.STREAM
}
