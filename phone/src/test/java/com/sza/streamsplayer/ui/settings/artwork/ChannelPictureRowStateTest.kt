package com.sza.streamsplayer.ui.settings.artwork

import com.sza.streamsplayer.domain.delivery.ExtensionStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S0064 G1: each status of a downloadable set reads as one row - its sentence, and only the buttons that
 * fit. The page draws nothing else, so these are what the user is shown.
 */
class ChannelPictureRowStateTest {

    @Test
    fun `a set that is not downloaded offers Download and no Remove`() {
        val state = ChannelPictureRowState.of(ExtensionStatus.NotInstalled)

        assertEquals(ChannelPictureStatus.NOT_INSTALLED, state.status)
        assertEquals(ChannelPictureAction.DOWNLOAD, state.action)
        assertFalse(state.canRemove)
    }

    @Test
    fun `an installed set offers Remove and no download`() {
        val state = ChannelPictureRowState.of(ExtensionStatus.Installed)

        assertEquals(ChannelPictureStatus.INSTALLED, state.status)
        assertEquals(ChannelPictureAction.NONE, state.action)
        assertTrue(state.canRemove)
    }

    @Test
    fun `a set with a newer copy offers Update and Remove`() {
        val state = ChannelPictureRowState.of(ExtensionStatus.UpdateAvailable)

        assertEquals(ChannelPictureStatus.UPDATE_READY, state.status)
        assertEquals(ChannelPictureAction.UPDATE, state.action)
        assertTrue(state.canRemove)
    }

    @Test
    fun `a running download shows its percent and offers nothing to press`() {
        val state = ChannelPictureRowState.of(ExtensionStatus.Downloading(42))

        assertEquals(ChannelPictureStatus.DOWNLOADING, state.status)
        assertEquals(42, state.percent)
        assertEquals(ChannelPictureAction.NONE, state.action)
        assertFalse(state.canRemove)
    }

    @Test
    fun `a percent outside zero to one hundred is held inside it`() {
        assertEquals(100, ChannelPictureRowState.of(ExtensionStatus.Downloading(150)).percent)
        assertEquals(0, ChannelPictureRowState.of(ExtensionStatus.Downloading(-5)).percent)
    }

    @Test
    fun `a failed row offers Try again and keeps Remove for a copy a failed update left behind`() {
        val state = ChannelPictureRowState.of(ExtensionStatus.Failed("timeout"))

        assertEquals(ChannelPictureStatus.FAILED, state.status)
        assertEquals(ChannelPictureAction.RETRY, state.action)
        assertTrue(state.canRemove)
    }

    @Test
    fun `a row whose status has not arrived offers nothing`() {
        val state = ChannelPictureRowState.Checking

        assertEquals(ChannelPictureStatus.CHECKING, state.status)
        assertEquals(ChannelPictureAction.NONE, state.action)
        assertFalse(state.canRemove)
    }

    @Test
    fun `the published size rounds up to whole megabytes`() {
        assertEquals(14, publishedMegabytes(14_605_058L))
        assertEquals(10, publishedMegabytes(10_005_648L))
        assertEquals(1, publishedMegabytes(1_048_576L))
        assertEquals(2, publishedMegabytes(1_048_577L))
    }

    @Test
    fun `no size, or a size that is nothing, is no size`() {
        assertNull(publishedMegabytes(null))
        assertNull(publishedMegabytes(0L))
        assertNull(publishedMegabytes(-1L))
    }
}
