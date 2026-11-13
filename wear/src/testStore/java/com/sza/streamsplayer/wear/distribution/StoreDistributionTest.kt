package com.sza.streamsplayer.wear.distribution

import org.junit.Assert.assertEquals
import org.junit.Test

class StoreDistributionTest {
    @Test
    fun `the store edition identifies itself as store`() {
        assertEquals("store", StoreDistribution().id)
    }
}
