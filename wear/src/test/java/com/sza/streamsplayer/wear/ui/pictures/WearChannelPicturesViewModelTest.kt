package com.sza.streamsplayer.wear.ui.pictures

import com.sza.streamsplayer.wear.data.delivery.ArtworkManifestClient
import com.sza.streamsplayer.wear.data.delivery.WearArtworkDelivery
import com.sza.streamsplayer.wear.data.repository.WearArtworkStore
import com.sza.streamsplayer.wear.domain.delivery.ArtworkManifest
import com.sza.streamsplayer.wear.domain.delivery.ArtworkManifestSet
import com.sza.streamsplayer.wear.domain.delivery.DeliverableSet
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * S0064: what the screen's view model asks of the delivery and what it shows back. The delivery and the
 * disk are doubles; the real removal and download are covered by `WearArtworkDeliveryTest`.
 *
 * The disk read and the manifest read finish on background threads, so these tests wait on real time
 * (`runBlocking`) for the state they expect instead of on the test scheduler's virtual clock.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WearChannelPicturesViewModelTest {

    private val logo = DeliverableSet.STREAM_LOGO_ATLAS
    private val preview = DeliverableSet.CHANNEL_PREVIEW_ATLAS

    private val delivery = mockk<WearArtworkDelivery>(relaxed = true)
    private val store = mockk<WearArtworkStore>()
    private val manifest = mockk<ArtworkManifestClient>()
    // Read on the view model's disk thread, written by the test thread: a plain HashSet would be a data race.
    private val held: MutableSet<DeliverableSet> = java.util.concurrent.ConcurrentHashMap.newKeySet()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        every { store.tilePackFile(any()) } answers { if (firstArg<DeliverableSet>() in held) File("pack.zip") else null }
        coEvery { manifest.fetch() } returns null
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = WearChannelPicturesViewModel(delivery, store, manifest)

    private fun <T> await(block: suspend () -> T): T = runBlocking { withTimeout(WAIT_MILLIS) { block() } }

    private fun WearChannelPicturesViewModel.awaitState(accepts: (WearChannelPicturesState) -> Boolean) =
        await { state.first(accepts) }

    private fun published(): ArtworkManifest = ArtworkManifest(
        schemaVersion = 1,
        generatedAt = "t",
        sets = mapOf(
            preview to ArtworkManifestSet(stamp = "p", totalBytes = 14_605_058L),
            logo to ArtworkManifestSet(stamp = "l", totalBytes = 10_005_648L),
        ),
    )

    @Test
    fun `nothing is offered before the disk was read`() {
        val state = viewModel().state.value

        assertEquals(WearChannelPictureRowState.Checking, state.rowOf(logo))
        assertEquals(WearChannelPictureRowState.Checking, state.rowOf(preview))
    }

    @Test
    fun `reload reads the disk and offers Download for a set the watch does not hold`() {
        held += preview
        val viewModel = viewModel()

        viewModel.reload()
        val state = viewModel.awaitState { it.installed != null }

        assertEquals(WearChannelPictureStatus.INSTALLED, state.rowOf(preview).status)
        assertTrue(state.rowOf(preview).canRemove)
        assertEquals(WearChannelPictureAction.DOWNLOAD, state.rowOf(logo).action)
    }

    @Test
    fun `reload reads the published sizes from the manifest`() {
        coEvery { manifest.fetch() } returns published()
        val viewModel = viewModel()

        viewModel.reload()
        val state = viewModel.awaitState { it.megabytes.isNotEmpty() }

        assertEquals(14, state.megabytes[preview])
        assertEquals(10, state.megabytes[logo])
    }

    @Test
    fun `a manifest with no answer leaves the rows without a size and the buttons in place`() {
        val viewModel = viewModel()

        viewModel.reload()
        val state = viewModel.awaitState { it.installed != null }

        assertTrue(state.megabytes.isEmpty())
        assertEquals(WearChannelPictureAction.DOWNLOAD, state.rowOf(logo).action)
    }

    @Test
    fun `download marks the row as running and then reads the disk`() {
        val gate = CompletableDeferred<Boolean>()
        coEvery { delivery.download(logo) } coAnswers { gate.await() }
        val viewModel = viewModel()

        viewModel.download(logo)
        assertTrue(logo in viewModel.state.value.busy)

        held += logo
        gate.complete(true)
        val state = viewModel.awaitState { logo !in it.busy && it.installed != null }

        assertEquals(WearChannelPictureStatus.INSTALLED, state.rowOf(logo).status)
        assertTrue(state.failed.isEmpty())
    }

    @Test
    fun `a second tap while the set downloads starts nothing`() {
        val gate = CompletableDeferred<Boolean>()
        coEvery { delivery.download(logo) } coAnswers { gate.await() }
        val viewModel = viewModel()

        viewModel.download(logo)
        viewModel.download(logo)
        gate.complete(true)
        viewModel.awaitState { logo !in it.busy && it.installed != null }

        coVerify(exactly = 1) { delivery.download(logo) }
    }

    @Test
    fun `a failed download reads Failed with Try again`() {
        coEvery { delivery.download(logo) } returns false
        val viewModel = viewModel()

        viewModel.download(logo)
        val state = viewModel.awaitState { logo !in it.busy && it.installed != null }

        assertEquals(WearChannelPictureStatus.FAILED, state.rowOf(logo).status)
        assertEquals(WearChannelPictureAction.RETRY, state.rowOf(logo).action)
    }

    @Test
    fun `a download that cannot record the request reads Failed instead of ending the screen`() {
        coEvery { delivery.download(logo) } throws java.io.IOException("disk full")
        val viewModel = viewModel()

        viewModel.download(logo)
        val state = viewModel.awaitState { logo !in it.busy && it.installed != null }

        assertEquals(WearChannelPictureStatus.FAILED, state.rowOf(logo).status)
    }

    @Test
    fun `a new download clears the failure of the earlier one`() {
        coEvery { delivery.download(logo) } returns false andThen true
        val viewModel = viewModel()
        viewModel.download(logo)
        viewModel.awaitState { logo in it.failed && logo !in it.busy }

        held += logo
        viewModel.download(logo)
        val state = viewModel.awaitState { logo !in it.busy && logo !in it.failed && it.installed?.get(logo) == true }

        assertFalse(logo in state.failed)
        assertEquals(WearChannelPictureStatus.INSTALLED, state.rowOf(logo).status)
    }

    @Test
    fun `remove asks the delivery and then shows the set as not held`() {
        held += logo
        coEvery { delivery.remove(logo) } coAnswers { held -= logo }
        val viewModel = viewModel()
        viewModel.reload()
        viewModel.awaitState { it.installed?.get(logo) == true }

        viewModel.remove(logo)
        val state = viewModel.awaitState { it.installed?.get(logo) == false }

        coVerify(exactly = 1) { delivery.remove(logo) }
        assertEquals(WearChannelPictureAction.DOWNLOAD, state.rowOf(logo).action)
        assertFalse(state.rowOf(logo).canRemove)
    }

    @Test
    fun `remove is ignored while the set downloads`() {
        val gate = CompletableDeferred<Boolean>()
        coEvery { delivery.download(logo) } coAnswers { gate.await() }
        val viewModel = viewModel()
        viewModel.download(logo)

        viewModel.remove(logo)
        gate.complete(false)
        viewModel.awaitState { logo !in it.busy && it.installed != null }

        coVerify(exactly = 0) { delivery.remove(any()) }
    }

    private companion object {
        const val WAIT_MILLIS = 10_000L
    }
}
