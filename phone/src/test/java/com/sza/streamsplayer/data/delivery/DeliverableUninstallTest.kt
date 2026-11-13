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
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * S0064 G2: removing a set. The repository takes the payload, the flag and the stamp together, in that
 * order, so a stamp never outlives its payload (the S1200 hazard); the inventory stops a job that outlived
 * its screen before it deletes anything, and leaves the row reading "not installed".
 */
class DeliverableUninstallTest {

    private val set = DeliverableSet.CHANNEL_PREVIEW_ATLAS

    @Test
    fun `the repository deletes the payload, then drops the flag and the stamp`() = runTest {
        val store = mockk<InstalledSetMarkerStore>(relaxed = true)

        DeliverableCapabilityRepositoryImpl(store).uninstall(set)

        coVerifyOrder {
            store.deletePayload(set)
            store.setInstalled(set, false)
            store.clearStamp(set)
        }
    }

    @Test
    fun `the inventory cancels the running job before it deletes the payload`() = runTest {
        val runner = mockk<DeliverableDownloadRunner>(relaxed = true)
        val repository = mockk<DeliverableCapabilityRepository>(relaxed = true)
        val inventory = DeliverableInventoryImpl(runner, repository, mockk<ArtworkManifestSource>())

        inventory.uninstall(moduleOf(inventory))

        coVerifyOrder {
            runner.cancel(set)
            repository.uninstall(set)
        }
    }

    @Test
    fun `removal touches only the set it was asked for`() = runTest {
        val runner = mockk<DeliverableDownloadRunner>(relaxed = true)
        val repository = mockk<DeliverableCapabilityRepository>(relaxed = true)
        val inventory = DeliverableInventoryImpl(runner, repository, mockk<ArtworkManifestSource>())

        inventory.uninstall(moduleOf(inventory))

        coVerify(exactly = 0) { repository.uninstall(DeliverableSet.STREAM_LOGO_ATLAS) }
    }

    @Test
    fun `a row left on a failed download reads not installed after removal`() = runTest {
        val runner = mockk<DeliverableDownloadRunner>(relaxed = true)
        val repository = mockk<DeliverableCapabilityRepository>(relaxed = true)
        val manifest = mockk<ArtworkManifestSource>()
        coEvery { manifest.stampOf(any()) } returns null
        every { repository.stateOf(any()) } returns flowOf(DeliverableCapability.NOT_INSTALLED)
        every { runner.progressOf(set) } returns flowOf(DownloadProgress.Failed("boom"))
        val inventory = DeliverableInventoryImpl(runner, repository, manifest)
        val module = moduleOf(inventory)
        inventory.download(module).toList()
        assertEquals(ExtensionStatus.Failed("boom"), module.statusFlow.first())

        inventory.uninstall(module)

        assertEquals(ExtensionStatus.NotInstalled, module.statusFlow.first())
    }

    private fun moduleOf(inventory: DeliverableInventoryImpl): ExtensionItem.Module =
        inventory.getExtensions().filterIsInstance<ExtensionItem.Module>().first { it.set == set }
}
