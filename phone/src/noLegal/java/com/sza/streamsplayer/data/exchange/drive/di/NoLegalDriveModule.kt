package com.sza.streamsplayer.data.exchange.drive.di

import com.sza.streamsplayer.data.exchange.drive.DriveAuthorizer
import com.sza.streamsplayer.data.exchange.drive.DriveTokenSource
import com.sza.streamsplayer.data.exchange.drive.GoogleDriveAuthorizer
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * The `noLegal` variant carries Google Play services for the Data Layer already, so it announces through Google
 * Drive too (S0029 owner input, recommended option). Narrowing Drive to `store` means binding a signed-out
 * authorizer here instead - this module is the one place that changes.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class NoLegalDriveModule {

    @Binds
    @Singleton
    abstract fun bindAuthorizer(impl: GoogleDriveAuthorizer): DriveAuthorizer

    @Binds
    @Singleton
    abstract fun bindTokenSource(impl: GoogleDriveAuthorizer): DriveTokenSource
}
