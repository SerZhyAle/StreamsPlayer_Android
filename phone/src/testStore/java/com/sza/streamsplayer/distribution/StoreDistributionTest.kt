package com.sza.streamsplayer.distribution

import org.junit.Assert.assertEquals
import org.junit.Test

class StoreDistributionTest {
    @Test
    fun `the store edition identifies itself as store`() {
        assertEquals("store", StoreDistribution().id)
    }

    @Test
    fun `the store edition is updated from its Play listing`() {
        assertEquals(
            "https://play.google.com/store/apps/details?id=com.sza.streamsplayer",
            StoreDistribution().updatesUrl
        )
    }
}
