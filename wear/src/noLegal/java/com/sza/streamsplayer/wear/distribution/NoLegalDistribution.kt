package com.sza.streamsplayer.wear.distribution

import javax.inject.Inject

class NoLegalDistribution @Inject constructor() : Distribution {
    override val id: String = "noLegal"
}
