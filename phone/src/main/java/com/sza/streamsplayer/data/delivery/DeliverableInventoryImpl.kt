package com.sza.streamsplayer.data.delivery

import com.sza.streamsplayer.domain.delivery.ArtworkManifestSource
import com.sza.streamsplayer.domain.delivery.DeliverableCapability
import com.sza.streamsplayer.domain.delivery.DeliverableCapabilityRepository
import com.sza.streamsplayer.domain.delivery.DeliverableDownloadRunner
import com.sza.streamsplayer.domain.delivery.DeliverableInventory
import com.sza.streamsplayer.domain.delivery.DeliverableSet
import com.sza.streamsplayer.domain.delivery.DownloadProgress
import com.sza.streamsplayer.domain.delivery.ExtensionItem
import com.sza.streamsplayer.domain.delivery.ExtensionStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Aggregates the deliverable artwork modules into a single inventory for the offer that follows a
 * catalog update (S0386 Phase 08). Module state comes from [DeliverableCapabilityRepository].
 */
@Singleton
class DeliverableInventoryImpl @Inject constructor(
    private val runner: DeliverableDownloadRunner,
    private val repository: DeliverableCapabilityRepository,
    // S1483: the freshness signal for the unpinned artwork payloads, read from the mirror.
    private val artworkManifest: ArtworkManifestSource
) : DeliverableInventory {

    // One status flow per item id; shared between the UI status flow and the download driver so
    // progress emitted during a download is observed by the list row.
    private val activeDownloads = ConcurrentHashMap<String, MutableStateFlow<ExtensionStatus>>()

    override fun getExtensions(): List<ExtensionItem> = buildList {
        add(moduleItem(DeliverableSet.CHANNEL_PREVIEW_ATLAS))
        add(moduleItem(DeliverableSet.STREAM_LOGO_ATLAS))
    }

    private fun moduleItem(set: DeliverableSet): ExtensionItem.Module = ExtensionItem.Module(
        id = moduleKey(set),
        set = set,
        statusFlow = moduleStatusFlow(set)
    )

    override fun download(item: ExtensionItem): Flow<DownloadProgress> {
        val statusFlow = statusFlowFor(item)
        return flow {
            val progressFlow = when (item) {
                is ExtensionItem.Module -> {
                    // S1200: force past the runner's installed-payload guard when the copy on disk is
                    // not the one the manifest names - otherwise "update" silently does nothing.
                    runner.enqueue(item.set, force = isStale(item.set))
                    runner.progressOf(item.set)
                }
            }
            var reachedTerminal = false
            try {
                collectInto(progressFlow, statusFlow) { reachedTerminal = true }
            } finally {
                // S3827: the collector dies with the screen while WorkManager keeps downloading. A
                // `Downloading` left in this process-wide flow would outrank the repository's
                // INSTALLED for the rest of the process, so drop it and let the persisted state decide.
                if (!reachedTerminal && statusFlow.value is ExtensionStatus.Downloading) {
                    statusFlow.value = ExtensionStatus.NotInstalled
                }
            }
        }
    }

    override suspend fun uninstall(item: ExtensionItem) {
        when (item) {
            is ExtensionItem.Module -> {
                // Cancel first: a job that outlived its screen would promote its payload over the empty slot.
                runner.cancel(item.set)
                repository.uninstall(item.set)
            }
        }
        statusFlowFor(item).value = ExtensionStatus.NotInstalled
    }

    private suspend fun FlowCollector<DownloadProgress>.collectInto(
        progressFlow: Flow<DownloadProgress>,
        statusFlow: MutableStateFlow<ExtensionStatus>,
        onTerminal: () -> Unit
    ) {
        progressFlow.collect { progress ->
            statusFlow.value = progress.toExtensionStatus()
            if (progress == DownloadProgress.Installed || progress is DownloadProgress.Failed) onTerminal()
            emit(progress)
        }
    }

    // Merges the persisted capability state with any in-flight download so the row reflects both:
    // a live download (or its failure) wins, otherwise the repository's installed/not state shows.
    private fun moduleStatusFlow(set: DeliverableSet): Flow<ExtensionStatus> {
        // Seeded NotInstalled without a payload stat: getExtensions() runs on the main thread, and the
        // seed is never shown - the combine below reads this flow only for Downloading/Failed, while
        // Installed comes from the repository's state flow.
        val active = activeDownloads.getOrPut(moduleKey(set)) {
            MutableStateFlow(ExtensionStatus.NotInstalled)
        }
        return combine(repository.stateOf(set), active) { cap, download ->
            when {
                download is ExtensionStatus.Downloading || download is ExtensionStatus.Failed -> download
                cap != DeliverableCapability.INSTALLED -> ExtensionStatus.NotInstalled
                isStale(set) -> ExtensionStatus.UpdateAvailable
                else -> ExtensionStatus.Installed
            }
        }.distinctUntilChanged()
    }

    /**
     * S1483: true only when the published manifest names a different stamp than the installed copy.
     *
     * STREAM-BANK rule 12: a manifest that is absent, unreachable, unparseable or of a higher schema
     * means "nothing new", never a prompt. So no answer is simply not stale; there is no compiled
     * identity to fall back to, because the artwork sets carry no pin.
     */
    private suspend fun isStale(set: DeliverableSet): Boolean {
        val expected = artworkManifest.stampOf(set) ?: return false
        return repository.isStale(set, expected)
    }

    private fun statusFlowFor(item: ExtensionItem): MutableStateFlow<ExtensionStatus> =
        activeDownloads.getOrPut(item.id) { MutableStateFlow(ExtensionStatus.NotInstalled) }

    private fun DownloadProgress.toExtensionStatus(): ExtensionStatus = when (this) {
        DownloadProgress.Queued -> ExtensionStatus.Downloading(0)
        is DownloadProgress.Running -> ExtensionStatus.Downloading(percent)
        DownloadProgress.Verifying -> ExtensionStatus.Downloading(99)
        DownloadProgress.Installed -> ExtensionStatus.Installed
        is DownloadProgress.Failed -> ExtensionStatus.Failed(reason)
    }

    private fun moduleKey(set: DeliverableSet): String = "set_${set.name.lowercase(Locale.ROOT)}"
}
