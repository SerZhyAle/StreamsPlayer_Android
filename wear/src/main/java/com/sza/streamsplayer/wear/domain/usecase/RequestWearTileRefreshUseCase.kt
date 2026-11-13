package com.sza.streamsplayer.wear.domain.usecase

import android.content.Context
import androidx.wear.tiles.TileService
import com.sza.streamsplayer.wear.domain.model.WearTileKind
import com.sza.streamsplayer.wear.tile.WearStreamTileService
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * S1955: requests the Wear OS system to redraw a tile when its content changes.
 *
 * Tile updates are event-driven only - no freshness interval is configured.
 */
class RequestWearTileRefreshUseCase @Inject constructor(
    @ApplicationContext private val context: Context
) {

    operator fun invoke(kind: WearTileKind) {
        val serviceClass = WearStreamTileService::class.java
        TileService.getUpdater(context).requestUpdate(serviceClass)
    }
}
