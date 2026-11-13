package com.sza.streamsplayer.ui.streams

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S0064 G2: the list stops drawing a removed set the next time the user is on it. The screen's `onStart`
 * re-reads the artwork index exactly when it disagrees with the disk, in either direction.
 */
class ArtworkReloadRuleTest {

    @Test
    fun `a payload that appeared while the screen was away is read`() {
        assertTrue(artworkIndexIsOutOfStep(indexLoaded = false, payloadInstalled = true))
    }

    @Test
    fun `a payload removed while the screen was away drops its pictures`() {
        assertTrue(artworkIndexIsOutOfStep(indexLoaded = true, payloadInstalled = false))
    }

    @Test
    fun `an index that agrees with the disk is left alone`() {
        assertFalse(artworkIndexIsOutOfStep(indexLoaded = true, payloadInstalled = true))
        assertFalse(artworkIndexIsOutOfStep(indexLoaded = false, payloadInstalled = false))
    }
}
