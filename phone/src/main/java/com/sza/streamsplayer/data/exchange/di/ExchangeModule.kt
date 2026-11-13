package com.sza.streamsplayer.data.exchange.di

import com.sza.streamsplayer.data.exchange.EntryStore
import com.sza.streamsplayer.data.exchange.ExchangeConnector
import com.sza.streamsplayer.data.exchange.RoomEntryStore
import com.sza.streamsplayer.data.exchange.RoomSecretStore
import com.sza.streamsplayer.data.exchange.SecretStore
import com.sza.streamsplayer.data.exchange.TlsExchangeConnector
import com.sza.streamsplayer.data.exchange.security.AndroidKeyStoreCipher
import com.sza.streamsplayer.BuildConfig
import com.sza.streamsplayer.data.exchange.security.KeyCipher
import com.sza.streamsplayer.data.exchange.session.ExchangeProductVersion
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ExchangeModule {

    @Binds
    @Singleton
    abstract fun bindBroadcastPasswordStore(
        impl: com.sza.streamsplayer.data.repository.settings.KeystoreBroadcastPasswordStore
    ): com.sza.streamsplayer.domain.repository.BroadcastPasswordStore


    @Binds
    @Singleton
    abstract fun bindExchangeConnector(
        impl: TlsExchangeConnector
    ): ExchangeConnector

    companion object {
        /** What the server records as this device's `productVersion` (6.1): the app's own version name. */
        @Provides
        @Singleton
        fun provideProductVersion(): ExchangeProductVersion = ExchangeProductVersion(BuildConfig.VERSION_NAME)
    }

    @Binds
    @Singleton
    abstract fun bindSecretStore(
        impl: RoomSecretStore
    ): SecretStore

    @Binds
    @Singleton
    abstract fun bindEntryStore(
        impl: RoomEntryStore
    ): EntryStore

    @Binds
    @Singleton
    abstract fun bindKeyCipher(
        impl: AndroidKeyStoreCipher
    ): KeyCipher

    @Binds
    @Singleton
    abstract fun bindDeviceExchangeE2eKeyStore(
        impl: com.sza.streamsplayer.data.repository.settings.KeystoreDeviceExchangeE2eKeyStore
    ): com.sza.streamsplayer.data.exchange.session.DeviceExchangeE2eKeyStore

    @Binds
    @Singleton
    abstract fun bindExchangeE2eTrust(
        impl: com.sza.streamsplayer.data.repository.settings.ExchangeE2eTrustStore
    ): com.sza.streamsplayer.data.exchange.directory.ExchangeE2eTrust

    @Binds
    @Singleton
    abstract fun bindDirectoryOpener(
        impl: com.sza.streamsplayer.data.exchange.directory.SealedDirectoryOpener
    ): com.sza.streamsplayer.data.exchange.directory.DirectoryOpener

    @Binds
    @Singleton
    abstract fun bindBroadcastTunnelIdentityProvider(
        impl: com.sza.streamsplayer.data.broadcast.exchange.BroadcastTunnelIdentities
    ): com.sza.streamsplayer.data.exchange.BroadcastTunnelIdentityProvider
}
