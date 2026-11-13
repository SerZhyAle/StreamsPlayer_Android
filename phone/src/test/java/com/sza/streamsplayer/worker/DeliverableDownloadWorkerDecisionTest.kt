package com.sza.streamsplayer.worker

import com.sza.streamsplayer.domain.delivery.DeliverableSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A forced run must reach the downloader: an accepted update has a stale payload on disk, and returning
 * success for it would record the new stamp over the old pictures.
 */
class DeliverableDownloadWorkerDecisionTest {

    @Test
    fun `an unforced run over an installed payload has nothing to do`() {
        assertTrue(DeliverableDownloadWorker.isAlreadyDone(force = false, installed = true))
    }

    @Test
    fun `a forced run over an installed payload still downloads`() {
        assertFalse(DeliverableDownloadWorker.isAlreadyDone(force = true, installed = true))
    }

    @Test
    fun `nothing installed always downloads`() {
        assertFalse(DeliverableDownloadWorker.isAlreadyDone(force = false, installed = false))
    }

    @Test
    fun `an unknown set name is refused rather than thrown`() {
        assertEquals(DeliverableSet.STREAM_LOGO_ATLAS, DeliverableDownloadWorker.deliverableSetNamed("STREAM_LOGO_ATLAS"))
        assertNull(DeliverableDownloadWorker.deliverableSetNamed("TRANSLATION"))
        assertNull(DeliverableDownloadWorker.deliverableSetNamed(null))
    }
}
