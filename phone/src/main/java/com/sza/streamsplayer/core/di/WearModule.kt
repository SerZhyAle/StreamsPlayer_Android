package com.sza.streamsplayer.core.di

import com.sza.streamsplayer.data.wear.WearableDataLayerRepositoryImpl
import com.sza.streamsplayer.domain.repository.WearableDataLayerRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class WearModule {
    @Binds abstract fun bridge(impl: WearableDataLayerRepositoryImpl): WearableDataLayerRepository
    companion object {
        @dagger.Provides @javax.inject.Singleton
        fun gson(): com.google.gson.Gson = com.google.gson.Gson()
    }
}
