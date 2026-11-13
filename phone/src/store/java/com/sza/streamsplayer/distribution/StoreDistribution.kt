package com.sza.streamsplayer.distribution

import javax.inject.Inject

class StoreDistribution @Inject constructor() : Distribution {
    override val id: String = "store"

    // The listing address is derived from the frozen application id, the one Play publishes this edition under.
    override val updatesUrl: String = "https://play.google.com/store/apps/details?id=com.sza.streamsplayer"
}
