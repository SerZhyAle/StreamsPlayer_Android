package com.sza.streamsplayer.wear.di

import android.content.Context
import com.sza.streamsplayer.data.exchange.BroadcastTunnelIdentityProvider
import com.sza.streamsplayer.data.exchange.EntryStore
import com.sza.streamsplayer.data.exchange.ExchangeConnector
import com.sza.streamsplayer.data.exchange.SecretStore
import com.sza.streamsplayer.data.exchange.TlsExchangeConnector
import com.sza.streamsplayer.data.exchange.directory.DirectoryOpener
import com.sza.streamsplayer.data.exchange.directory.ExchangeE2eTrust
import com.sza.streamsplayer.data.exchange.directory.SealedDirectoryOpener
import com.sza.streamsplayer.data.exchange.security.AndroidKeyStoreCipher
import com.sza.streamsplayer.data.exchange.session.DeviceExchangeE2eKeyStore
import com.sza.streamsplayer.data.exchange.session.ExchangeProductVersion
import com.sza.streamsplayer.wear.BuildConfig
import com.sza.streamsplayer.wear.data.broadcast.exchange.BroadcastTunnelIdentities
import com.sza.streamsplayer.wear.data.exchange.EncryptedWearExchangeE2eStore
import com.sza.streamsplayer.wear.data.exchange.WearExchangeE2eStore
import com.sza.streamsplayer.wear.data.exchange.WearExchangeE2eTrust
import com.sza.streamsplayer.wear.data.exchange.WearExchangeEntryStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object WearExchangeModule {
    @Provides @Singleton
    fun store(@ApplicationContext context: Context, cipher: AndroidKeyStoreCipher) = WearExchangeEntryStore(
        context.getSharedPreferences("watch_exchange", Context.MODE_PRIVATE), cipher,
    )

    @Provides fun entries(store: WearExchangeEntryStore): EntryStore = store
    @Provides fun secrets(store: WearExchangeEntryStore): SecretStore = store
    @Provides fun connector(impl: TlsExchangeConnector): ExchangeConnector = impl

    /** What the server records as this device's `productVersion` (6.1): the watch app's own version name. */
    @Provides @Singleton
    fun productVersion(): ExchangeProductVersion = ExchangeProductVersion(BuildConfig.VERSION_NAME)

    @Provides @Singleton
    fun e2eStore(@ApplicationContext context: Context, cipher: AndroidKeyStoreCipher): WearExchangeE2eStore =
        EncryptedWearExchangeE2eStore(context.getSharedPreferences("watch_exchange_e2e", Context.MODE_PRIVATE), cipher)

    @Provides
    fun e2eKeyStore(store: WearExchangeE2eStore): DeviceExchangeE2eKeyStore = store

    @Provides
    fun e2eTrust(impl: WearExchangeE2eTrust): ExchangeE2eTrust = impl

    @Provides @Singleton
    fun directoryOpener(impl: SealedDirectoryOpener): DirectoryOpener = impl

    @Provides @Singleton
    fun tunnelIdentityProvider(impl: BroadcastTunnelIdentities): BroadcastTunnelIdentityProvider = impl
}
