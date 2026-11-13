package com.sza.streamsplayer.wear.distribution

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class StoreDistributionModule {
    @Binds
    abstract fun bindDistribution(implementation: StoreDistribution): Distribution
}
