package com.sza.streamsplayer.distribution

import org.junit.Assert.assertEquals
import org.junit.Test

class NoLegalDistributionTest {
    @Test
    fun `the noLegal edition identifies itself as noLegal`() {
        assertEquals("noLegal", NoLegalDistribution().id)
    }

    @Test
    fun `the noLegal edition is updated from the repository releases`() {
        assertEquals("https://github.com/SerZhyAle/StreamsPlayer_Android/releases", NoLegalDistribution().updatesUrl)
    }
}
