package com.sza.streamsplayer.wear.exchange

import com.sza.streamsplayer.wear.data.exchange.WearExchangeAccountRepository
import com.sza.streamsplayer.wear.data.exchange.WearExchangeEntryStore
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Debug instrumentation reaches the real graph without adding a production command or exported component. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface WatchExchangeSmokeEntryPoint {
    fun accounts(): WearExchangeAccountRepository
    fun entries(): WearExchangeEntryStore
}
