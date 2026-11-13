package com.sza.streamsplayer.ui.settings.artwork

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sza.streamsplayer.domain.delivery.ArtworkManifestSource
import com.sza.streamsplayer.domain.delivery.DeliverableInventory
import com.sza.streamsplayer.domain.delivery.DeliverableSet
import com.sza.streamsplayer.domain.delivery.ExtensionItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * Drives the channel-pictures rows of the General settings page (S0064). Ported from the Extensions
 * Manager's view model, cut to what two rows need: no bulk install or delete.
 */
@HiltViewModel
class ChannelPicturesViewModel @Inject constructor(
    private val inventory: DeliverableInventory,
    private val manifest: ArtworkManifestSource,
) : ViewModel() {

    val extensions: List<ExtensionItem> = inventory.getExtensions()

    // The download flow never completes, so a retry tap must replace the previous collector, not add one.
    private val downloadJobs = mutableMapOf<String, Job>()

    fun download(item: ExtensionItem) {
        Timber.d("S0064: phone channel pictures download tapped, set %s", item.id)
        val previous = downloadJobs[item.id]
        downloadJobs[item.id] = viewModelScope.launch {
            // Joined, so the old collector's cleanup of a mid-download status lands before the new one writes.
            previous?.cancelAndJoin()
            inventory.download(item).collect {
                // Progress is reactively piped via statusFlow of each item
            }
        }
    }

    fun uninstall(item: ExtensionItem) {
        Timber.d("S0064: phone channel pictures remove confirmed, set %s", item.id)
        viewModelScope.launch {
            inventory.uninstall(item)
        }
    }

    /** The published size of [set] in megabytes, read off the main thread; null while the manifest has no answer. */
    suspend fun sizeMegabytes(set: DeliverableSet): Int? = publishedMegabytes(manifest.sizeOf(set))
}
