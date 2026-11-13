package com.sza.streamsplayer.wear.di

import com.sza.streamsplayer.wear.data.delivery.WearArtworkOptOutStore
import com.sza.streamsplayer.wear.data.preferences.WearSettingsDataStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * S0064: the graph of the watch's channel-picture control, beside `WearBroadcastModule`.
 *
 * Unwraps the settings store here rather than binding `DataStore<Preferences>` into the graph, which
 * `WearSettingsDataStore` rules out - a bare binding of that type collides with the next store the app gains.
 */
@Module
@InstallIn(SingletonComponent::class)
object WearArtworkModule {

    @Provides
    @Singleton
    fun provideWearArtworkOptOutStore(settings: WearSettingsDataStore): WearArtworkOptOutStore =
        WearArtworkOptOutStore(settings.store)
}
