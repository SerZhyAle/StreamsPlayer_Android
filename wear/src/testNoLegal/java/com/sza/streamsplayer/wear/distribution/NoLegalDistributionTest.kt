package com.sza.streamsplayer.wear.distribution

import org.junit.Assert.assertEquals
import org.junit.Test

class NoLegalDistributionTest {
    @Test
    fun `the noLegal edition identifies itself as noLegal`() {
        assertEquals("noLegal", NoLegalDistribution().id)
    }
}
