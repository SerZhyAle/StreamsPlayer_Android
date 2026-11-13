package com.sza.streamsplayer.core.di

import com.sza.streamsplayer.data.delivery.ArtworkManifestClient
import com.sza.streamsplayer.data.delivery.DeliverableCapabilityRepositoryImpl
import com.sza.streamsplayer.data.delivery.DeliverableDescriptorCatalog
import com.sza.streamsplayer.data.delivery.DeliverableDownloadRunnerImpl
import com.sza.streamsplayer.data.delivery.DeliverableInventoryImpl
import com.sza.streamsplayer.data.delivery.RealDeliverableSetDownloader
import com.sza.streamsplayer.data.delivery.StreamCatalogPublicationClient
import com.sza.streamsplayer.domain.delivery.ArtworkManifestSource
import com.sza.streamsplayer.domain.delivery.DeliverableCapabilityRepository
import com.sza.streamsplayer.domain.delivery.DeliverableDownloadRunner
import com.sza.streamsplayer.domain.delivery.DeliverableInventory
import com.sza.streamsplayer.domain.delivery.DeliverableSet
import com.sza.streamsplayer.domain.delivery.DeliverableSetDownloader
import com.sza.streamsplayer.domain.delivery.DeliverableSourceDescriptor
import com.sza.streamsplayer.domain.delivery.StreamCatalogPublicationSource
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Binds the on-demand delivery contract and channel (S0386 Phases 02/04). Both variants deliver the
 * same two artwork sets, so the module is one and lives in `src/main`.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class DeliveryModule {

    @Binds
    abstract fun bindDeliverableCapabilityRepository(
        impl: DeliverableCapabilityRepositoryImpl
    ): DeliverableCapabilityRepository

    @Binds
    abstract fun bindDeliverableSetDownloader(
        impl: RealDeliverableSetDownloader
    ): DeliverableSetDownloader

    @Binds
    @Singleton
    abstract fun bindDeliverableDownloadRunner(
        impl: DeliverableDownloadRunnerImpl
    ): DeliverableDownloadRunner

    @Binds
    @Singleton
    abstract fun bindDeliverableInventory(
        impl: DeliverableInventoryImpl
    ): DeliverableInventory

    /** S1483: freshness of the unpinned artwork payloads is read from the mirror's manifest. */
    @Binds
    @Singleton
    abstract fun bindArtworkManifestSource(
        impl: ArtworkManifestClient
    ): ArtworkManifestSource

    /** S0068: the catalog update offer reads what the mirror publishes now. */
    @Binds
    @Singleton
    abstract fun bindStreamCatalogPublicationSource(
        impl: StreamCatalogPublicationClient
    ): StreamCatalogPublicationSource

    companion object {

        /** Compiled descriptors keyed by set (S0386 Phase 05). */
        @Provides
        @Singleton
        fun provideDescriptors(): Map<DeliverableSet, @JvmSuppressWildcards DeliverableSourceDescriptor> = mapOf(
            DeliverableSet.CHANNEL_PREVIEW_ATLAS to DeliverableDescriptorCatalog.channelPreviewAtlas(),
            DeliverableSet.STREAM_LOGO_ATLAS to DeliverableDescriptorCatalog.streamLogoAtlas()
        )
    }
}
