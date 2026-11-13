package com.sza.streamsplayer.wear.ui.pictures

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sza.streamsplayer.wear.data.delivery.ArtworkManifestClient
import com.sza.streamsplayer.wear.data.delivery.WearArtworkDelivery
import com.sza.streamsplayer.wear.data.repository.WearArtworkStore
import com.sza.streamsplayer.wear.domain.delivery.DeliverableSet
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.IOException
import javax.inject.Inject

/**
 * Drives the watch's Channel pictures screen (S0064): two rows, each with its state, its published size
 * and the button that fits. The phone's counterpart is `ChannelPicturesViewModel`; this one has no source
 * twin, because the watch has no inventory and no download job to observe - the delivery is a plain
 * suspend call tied to this screen.
 *
 * A download belongs to this screen's scope: leaving the screen stops it, the set stays unremoved, and
 * the next pull to refresh in the channel list fetches it. Remove is not stopped by leaving.
 */
@HiltViewModel
class WearChannelPicturesViewModel @Inject constructor(
    private val delivery: WearArtworkDelivery,
    private val store: WearArtworkStore,
    private val manifest: ArtworkManifestClient,
) : ViewModel() {

    private val _state = MutableStateFlow(WearChannelPicturesState())
    val state: StateFlow<WearChannelPicturesState> = _state.asStateFlow()

    private var sizeJob: Job? = null

    /**
     * Reads what is on disk, and the published sizes if they have not arrived yet. The screen calls it
     * each time it comes to the front, so a refresh that ran over it from the channel list shows up.
     */
    fun reload() {
        viewModelScope.launch { readInstalled() }
        loadSizes()
    }

    fun download(set: DeliverableSet) {
        if (set in _state.value.busy) return
        _state.update { it.copy(busy = it.busy + set, failed = it.failed - set) }
        viewModelScope.launch {
            var succeeded = false
            try {
                succeeded = try {
                    delivery.download(set)
                } catch (e: IOException) {
                    // The record of the removal could not be written: nothing was fetched.
                    Timber.w(e, "Channel pictures: %s not downloaded", set)
                    false
                }
                readInstalled()
            } finally {
                _state.update { it.copy(busy = it.busy - set, failed = if (succeeded) it.failed else it.failed + set) }
            }
        }
    }

    fun remove(set: DeliverableSet) {
        if (set in _state.value.busy) return
        viewModelScope.launch {
            try {
                delivery.remove(set)
            } catch (e: IOException) {
                Timber.w(e, "Channel pictures: %s not removed", set)
            }
            _state.update { it.copy(failed = it.failed - set) }
            readInstalled()
        }
    }

    private suspend fun readInstalled() {
        val held = withContext(Dispatchers.IO) { WEAR_PICTURE_SETS.associateWith { store.tilePackFile(it) != null } }
        _state.update { it.copy(installed = held) }
    }

    /** The manifest names the sizes; with no answer the rows simply show no size line. */
    private fun loadSizes() {
        if (_state.value.megabytes.size == WEAR_PICTURE_SETS.size || sizeJob?.isActive == true) return
        sizeJob = viewModelScope.launch {
            val published = manifest.fetch() ?: return@launch
            val sizes = buildMap {
                for (set in WEAR_PICTURE_SETS) {
                    publishedMegabytes(published.sets[set]?.totalBytes)?.let { put(set, it) }
                }
            }
            _state.update { it.copy(megabytes = sizes) }
        }
    }
}
