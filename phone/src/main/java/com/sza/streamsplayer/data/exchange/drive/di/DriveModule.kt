package com.sza.streamsplayer.data.exchange.drive.di

import com.sza.streamsplayer.data.exchange.drive.DriveChannelStore
import com.sza.streamsplayer.data.exchange.drive.DriveFilesApi
import com.sza.streamsplayer.data.exchange.drive.DriveTokenSource
import com.sza.streamsplayer.data.exchange.drive.HttpDriveFilesApi
import com.sza.streamsplayer.data.exchange.drive.SharedPrefsDriveChannelStore
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import javax.inject.Singleton

/**
 * What every variant shares of the Drive channel (S0029). The Google side - the authorizer - is bound by the
 * variant's own module (`StoreDriveModule`, `NoLegalDriveModule`), as `CLAUDE.md` section 3 asks.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class DriveModule {

    @Binds
    @Singleton
    abstract fun bindChannelStore(impl: SharedPrefsDriveChannelStore): DriveChannelStore

    companion object {
        @Provides
        @Singleton
        fun provideDriveFilesApi(client: OkHttpClient, tokens: DriveTokenSource): DriveFilesApi =
            HttpDriveFilesApi(client, tokens)
    }
}
