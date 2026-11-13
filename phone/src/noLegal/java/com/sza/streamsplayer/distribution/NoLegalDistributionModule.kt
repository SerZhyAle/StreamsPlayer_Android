package com.sza.streamsplayer.distribution

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class NoLegalDistributionModule {
    @Binds
    abstract fun bindDistribution(implementation: NoLegalDistribution): Distribution
}
