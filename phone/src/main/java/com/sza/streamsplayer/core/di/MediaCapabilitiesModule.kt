package com.sza.streamsplayer.core.di

import com.sza.streamsplayer.core.capability.MediaCapabilities
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object MediaCapabilitiesModule {

    @Provides
    @Singleton
    fun provideMediaCapabilities(): MediaCapabilities = MediaCapabilities(
        supportsVideo = true,
        supportsAudio = true,
        supportsImages = false,
        supportsDocuments = false,
        supportsEpub = false,
        supportsCloud = false,
        supportsLocalNetworkSources = true,
        supportsDefaultPlayer = true,
        supportsCast = false,
        supportsMicRecording = true,
        supportsVrPlayer = false,
        supportsWearCompanion = false,
        supportsVrMediaControls = false,
    )
}
