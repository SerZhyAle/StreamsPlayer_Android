package com.sza.streamsplayer.data.delivery

import com.sza.streamsplayer.domain.delivery.ArtworkManifestSource
import com.sza.streamsplayer.domain.delivery.DeliverableCapability
import com.sza.streamsplayer.domain.delivery.DeliverableCapabilityRepository
import com.sza.streamsplayer.domain.delivery.DeliverableDownloadRunner
import com.sza.streamsplayer.domain.delivery.DeliverableSet
import com.sza.streamsplayer.domain.delivery.DownloadProgress
import com.sza.streamsplayer.domain.delivery.ExtensionItem
import com.sza.streamsplayer.domain.delivery.ExtensionStatus
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * STREAM-BANK rule 12: the stamp alone notices a change, and a manifest with no answer means "nothing
 * new", never a prompt. The offer reads these statuses, so they are what the user would be asked about.
 */
class DeliverableInventoryStalenessTest {

    private val set = DeliverableSet.STREAM_LOGO_ATLAS
    private val runner = mockk<DeliverableDownloadRunner>(relaxed = true)
    private val repository = mockk<DeliverableCapabilityRepository>()
    private val manifest = mockk<ArtworkManifestSource>()

    private fun item(): ExtensionItem.Module {
        every { repository.stateOf(any()) } returns flowOf(DeliverableCapability.INSTALLED)
        return DeliverableInventoryImpl(runner, repository, manifest)
            .getExtensions()
            .filterIsInstance<ExtensionItem.Module>()
            .first { it.set == set }
    }

    @Test
    fun `a manifest that does not answer never reads as an update`() = runTest {
        coEvery { manifest.stampOf(set) } returns null

        assertEquals(ExtensionStatus.Installed, item().statusFlow.first())
    }

    @Test
    fun `the same stamp is current`() = runTest {
        coEvery { manifest.stampOf(set) } returns "same"
        coEvery { repository.isStale(set, "same") } returns false

        assertEquals(ExtensionStatus.Installed, item().statusFlow.first())
    }

    @Test
    fun `another stamp is an update`() = runTest {
        coEvery { manifest.stampOf(set) } returns "newer"
        coEvery { repository.isStale(set, "newer") } returns true

        assertEquals(ExtensionStatus.UpdateAvailable, item().statusFlow.first())
    }

    @Test
    fun `accepting an update forces the download past the installed payload`() = runTest {
        coEvery { manifest.stampOf(set) } returns "newer"
        coEvery { repository.isStale(set, "newer") } returns true
        every { runner.progressOf(set) } returns flowOf(DownloadProgress.Installed)
        val inventory = DeliverableInventoryImpl(runner, repository, manifest)
        every { repository.stateOf(any()) } returns flowOf(DeliverableCapability.INSTALLED)
        val module = inventory.getExtensions().filterIsInstance<ExtensionItem.Module>().first { it.set == set }

        inventory.download(module).toList()

        verify(exactly = 1) { runner.enqueue(set, force = true) }
    }
}
