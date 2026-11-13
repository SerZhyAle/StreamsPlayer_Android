package com.sza.streamsplayer.data.exchange.drive.di

import com.sza.streamsplayer.data.exchange.drive.DriveAuthorizer
import com.sza.streamsplayer.data.exchange.drive.DriveTokenSource
import com.sza.streamsplayer.data.exchange.drive.GoogleDriveAuthorizer
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** The `store` variant announces through Google Drive with the Google authorizer (S0029). */
@Module
@InstallIn(SingletonComponent::class)
abstract class StoreDriveModule {

    @Binds
    @Singleton
    abstract fun bindAuthorizer(impl: GoogleDriveAuthorizer): DriveAuthorizer

    @Binds
    @Singleton
    abstract fun bindTokenSource(impl: GoogleDriveAuthorizer): DriveTokenSource
}
