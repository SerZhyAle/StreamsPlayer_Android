package com.sza.streamsplayer

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import com.sza.streamsplayer.domain.usecase.PushWearStreamPinsUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Inject
import timber.log.Timber

@HiltAndroidApp
class StreamsPlayerApplication : Application(), Configuration.Provider {
    // S0050: lazy, so a process that never queues a download never builds the factory.
    @Inject lateinit var workerFactory: dagger.Lazy<HiltWorkerFactory>
    @Inject lateinit var pushWearPins: PushWearStreamPinsUseCase
    @Inject lateinit var receiverRuntime: com.sza.streamsplayer.ui.exchange.ExchangeReceiverRuntime
    private val wearScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory.get())
            .build()
    override fun onCreate() {
        super.onCreate()
        // S0049: before any activity inflates, so the first frame already has the chosen theme.
        com.sza.streamsplayer.core.util.ThemeModeStore.applyStored(this)
        com.sza.streamsplayer.core.util.LocaleHelper.restoreForAppCompat(this)
        com.sza.streamsplayer.data.exchange.ExchangeLog.sink = { priority, cause, message ->
            Timber.log(priority, cause, message)
        }
        if (BuildConfig.DEBUG) Timber.plant(object : Timber.DebugTree() {
            override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
                super.log(priority, tag, com.sza.streamsplayer.data.exchange.BroadcastLogRedactor.redact(message), t)
            }
        })
        pushWearPins.observeAndPush(wearScope)
        receiverRuntime.start(this)
    }
}
