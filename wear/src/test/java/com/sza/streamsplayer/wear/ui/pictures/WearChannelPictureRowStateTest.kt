package com.sza.streamsplayer.wear.ui.pictures

import com.sza.streamsplayer.wear.domain.delivery.DeliverableSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** S0064: which status line and which buttons a row of the watch's Channel pictures screen shows. */
class WearChannelPictureRowStateTest {

    private val logo = DeliverableSet.STREAM_LOGO_ATLAS
    private val preview = DeliverableSet.CHANNEL_PREVIEW_ATLAS

    @Test
    fun `a set the watch does not hold offers Download and no Remove`() {
        val row = WearChannelPictureRowState.of(installed = false, downloading = false, failed = false)

        assertEquals(WearChannelPictureStatus.NOT_INSTALLED, row.status)
        assertEquals(WearChannelPictureAction.DOWNLOAD, row.action)
        assertFalse(row.canRemove)
    }

    @Test
    fun `a set the watch holds offers Remove and no Download`() {
        val row = WearChannelPictureRowState.of(installed = true, downloading = false, failed = false)

        assertEquals(WearChannelPictureStatus.INSTALLED, row.status)
        assertEquals(WearChannelPictureAction.NONE, row.action)
        assertTrue(row.canRemove)
    }

    @Test
    fun `a running download offers no button at all`() {
        val row = WearChannelPictureRowState.of(installed = false, downloading = true, failed = false)

        assertEquals(WearChannelPictureStatus.DOWNLOADING, row.status)
        assertEquals(WearChannelPictureAction.NONE, row.action)
        assertFalse(row.canRemove)
    }

    @Test
    fun `a running download hides Remove even for a set that is held`() {
        val row = WearChannelPictureRowState.of(installed = true, downloading = true, failed = true)

        assertEquals(WearChannelPictureStatus.DOWNLOADING, row.status)
        assertFalse(row.canRemove)
    }

    @Test
    fun `a failed download of a set that is not held offers Try again`() {
        val row = WearChannelPictureRowState.of(installed = false, downloading = false, failed = true)

        assertEquals(WearChannelPictureStatus.FAILED, row.status)
        assertEquals(WearChannelPictureAction.RETRY, row.action)
        assertFalse(row.canRemove)
    }

    @Test
    fun `a set that is held reads as held even after an earlier failure`() {
        val row = WearChannelPictureRowState.of(installed = true, downloading = false, failed = true)

        assertEquals(WearChannelPictureStatus.INSTALLED, row.status)
        assertTrue(row.canRemove)
    }

    @Test
    fun `before the disk is read the rows offer nothing`() {
        val row = WearChannelPicturesState().rowOf(logo)

        assertEquals(WearChannelPictureRowState.Checking, row)
        assertEquals(WearChannelPictureAction.NONE, row.action)
        assertFalse(row.canRemove)
    }

    @Test
    fun `each set reads its own entry of the disk snapshot`() {
        val state = WearChannelPicturesState(installed = mapOf(preview to true, logo to false))

        assertEquals(WearChannelPictureStatus.INSTALLED, state.rowOf(preview).status)
        assertEquals(WearChannelPictureStatus.NOT_INSTALLED, state.rowOf(logo).status)
    }

    @Test
    fun `a set missing from the snapshot reads as not held`() {
        val state = WearChannelPicturesState(installed = emptyMap())

        assertEquals(WearChannelPictureStatus.NOT_INSTALLED, state.rowOf(logo).status)
    }

    @Test
    fun `only the set being downloaded reads as downloading`() {
        val state = WearChannelPicturesState(
            installed = mapOf(preview to false, logo to false),
            busy = setOf(logo),
        )

        assertEquals(WearChannelPictureStatus.DOWNLOADING, state.rowOf(logo).status)
        assertEquals(WearChannelPictureStatus.NOT_INSTALLED, state.rowOf(preview).status)
    }

    @Test
    fun `the screen lists previews first and then logos`() {
        assertEquals(listOf(preview, logo), WEAR_PICTURE_SETS)
    }

    @Test
    fun `the published size is rounded up to whole megabytes as the phone's offer does`() {
        assertEquals(14, publishedMegabytes(14_605_058L))
        assertEquals(10, publishedMegabytes(10_005_648L))
        assertEquals(1, publishedMegabytes(1L))
        assertEquals(1, publishedMegabytes(1_048_576L))
        assertEquals(2, publishedMegabytes(1_048_577L))
    }

    @Test
    fun `no size is shown for a manifest that gave none`() {
        assertNull(publishedMegabytes(null))
        assertNull(publishedMegabytes(0L))
    }
}
