package com.sza.streamsplayer.domain.delivery

import kotlinx.coroutines.flow.Flow

sealed class ExtensionItem {
    abstract val id: String
    abstract val statusFlow: Flow<ExtensionStatus>

    data class Module(
        override val id: String,
        val set: DeliverableSet,
        override val statusFlow: Flow<ExtensionStatus>
    ) : ExtensionItem()
}

sealed class ExtensionStatus {
    object Installed : ExtensionStatus()
    object NotInstalled : ExtensionStatus()

    /**
     * S1200: the payload is present and usable, but it was installed against different pins than this
     * build carries - a newer (or simply other) version is what the app now expects.
     */
    object UpdateAvailable : ExtensionStatus()
    data class Downloading(val percent: Int) : ExtensionStatus()
    data class Failed(val error: String) : ExtensionStatus()
}

/**
 * Persists the registry/inventory of available deliverable items.
 * Reports statuses and manages installation (S0386 Phase 08).
 */
interface DeliverableInventory {
    fun getExtensions(): List<ExtensionItem>
    fun download(item: ExtensionItem): Flow<DownloadProgress>

    /** Removes the downloaded copy of [item]; its row reads not installed afterwards. */
    suspend fun uninstall(item: ExtensionItem)
}
