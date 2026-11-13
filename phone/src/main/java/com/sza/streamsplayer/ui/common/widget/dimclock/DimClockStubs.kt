package com.sza.streamsplayer.ui.common.widget.dimclock

import android.content.Context
import android.view.View
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DimClockStyleProvider @Inject constructor()

@Singleton
class DimStatusContentProvider @Inject constructor()

@Singleton
class DimChipIconLoader @Inject constructor()

@Singleton
class DimChipActionRouter @Inject constructor()

@Singleton
class DimClockInteractionHandler @Inject constructor()

class DimClockOverlayView(context: Context) : View(context) {
    var onDimExitRequested: (() -> Unit)? = null

    fun bind(
        styleProvider: DimClockStyleProvider?,
        statusProvider: DimStatusContentProvider?,
        state: Any?,
        iconLoader: DimChipIconLoader?,
        actionRouter: DimChipActionRouter?,
        interactionHandler: DimClockInteractionHandler?,
    ) = Unit

    fun onHostInteraction() = Unit
}
