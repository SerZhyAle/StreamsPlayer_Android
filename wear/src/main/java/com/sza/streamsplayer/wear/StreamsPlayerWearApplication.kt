package com.sza.streamsplayer.wear

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber

@HiltAndroidApp
class StreamsPlayerWearApplication : Application() {
    @javax.inject.Inject lateinit var exchange: com.sza.streamsplayer.wear.data.exchange.WearExchangeRuntime
    override fun onCreate() {
        super.onCreate()
        com.sza.streamsplayer.data.exchange.ExchangeLog.sink = { priority, cause, message ->
            Timber.log(priority, cause, message)
        }
        if (BuildConfig.DEBUG) Timber.plant(Timber.DebugTree())
        exchange.start()
    }
}
