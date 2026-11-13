package com.sza.streamsplayer.broadcast.di

import com.sza.streamsplayer.broadcast.BroadcastPreviewBinder
import com.sza.streamsplayer.broadcast.BroadcastPreviewProvider
import com.sza.streamsplayer.broadcast.BroadcastSourceController
import com.sza.streamsplayer.broadcast.BroadcastSourceControllerImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class BroadcastSourceModule {

    @Binds
    @Singleton
    abstract fun bindBroadcastSourceController(
        impl: BroadcastSourceControllerImpl
    ): BroadcastSourceController

    @Binds
    @Singleton
    abstract fun bindBroadcastPreviewBinder(
        impl: BroadcastPreviewProvider
    ): BroadcastPreviewBinder

    @Binds
    @Singleton
    abstract fun bindWatchCameraSessionAnnouncer(
        impl: com.sza.streamsplayer.broadcast.NoOpWatchCameraSessionAnnouncer
    ): com.sza.streamsplayer.broadcast.WatchCameraSessionAnnouncer
}
