package com.sza.streamsplayer.distribution

import javax.inject.Inject

class NoLegalDistribution @Inject constructor() : Distribution {
    override val id: String = "noLegal"

    // The sideload edition is published as release assets of the product repository.
    override val updatesUrl: String = "https://github.com/SerZhyAle/StreamsPlayer_Android/releases"
}
