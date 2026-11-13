package com.sza.streamsplayer.ui.common.widget

import com.sza.streamsplayer.domain.model.sensors.CompassReading
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DimHeadingProvider @Inject constructor() {
    fun setActive(active: Boolean) = Unit
    fun current(): CompassReading? = null
}
