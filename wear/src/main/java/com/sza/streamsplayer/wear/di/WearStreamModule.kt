package com.sza.streamsplayer.wear.di

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.DefaultRenderersFactory
import com.google.gson.Gson
import com.sza.streamsplayer.wear.data.network.*
import com.sza.streamsplayer.wear.data.preferences.*
import com.sza.streamsplayer.wear.data.repository.*
import com.sza.streamsplayer.wear.domain.repository.*
import com.sza.streamsplayer.wear.domain.playback.WearBackgroundSessionState
import com.sza.streamsplayer.wear.domain.recorder.VoiceRecordingStateHolder
import dagger.*
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class WearStreamModule {
    @Binds abstract fun preferences(impl: WearPreferencesRepositoryImpl): WearPreferencesRepository
    @Binds abstract fun channels(impl: WearStreamChannelRepositoryImpl): WearStreamChannelRepository
    @Binds abstract fun collections(impl: WearStreamCollectionRepositoryImpl): WearStreamCollectionRepository
    @Binds abstract fun usage(impl: WearStreamUsageRepositoryImpl): WearStreamUsageRepository
    @Binds abstract fun network(impl: StreamNetworkHoldManager): StreamNetworkHold
    @Binds abstract fun monitor(impl: WearNetworkChannelMonitorImpl): WearNetworkChannelMonitor
    @Binds abstract fun nowPlaying(impl: WearNowPlayingRepositoryImpl): WearNowPlayingRepository
    @Binds abstract fun tiles(impl: WearTileAssignmentRepositoryImpl): WearTileAssignmentRepository
    @Binds abstract fun openPhone(impl: WearOpenOnPhoneRepositoryImpl): WearOpenOnPhoneRepository
    @Binds abstract fun openUrl(impl: WearOpenUrlOnPhoneRepositoryImpl): WearOpenUrlOnPhoneRepository
    @Binds abstract fun systemInfo(impl: com.sza.streamsplayer.wear.data.wear.AndroidWearSystemInfoDataSource): WearSystemInfoDataSource

    companion object {
        @Provides @Singleton fun gson(): Gson = Gson()
        @Provides @Singleton fun http(): okhttp3.OkHttpClient = okhttp3.OkHttpClient()
        @Provides @Singleton fun background(): WearBackgroundSessionState = WearBackgroundSessionState()
        @Provides @Singleton fun recorder(): VoiceRecordingStateHolder = VoiceRecordingStateHolder()
        /** Unscoped: each screen owns and releases its own player, as in the donor. */
        @Provides fun player(@ApplicationContext context: Context): ExoPlayer = ExoPlayer.Builder(context,
            DefaultRenderersFactory(context).setEnableDecoderFallback(true))
            .setMediaSourceFactory(WearStreamDataSourceFactoryProvider.createMediaSourceFactory(context))
            .setAudioAttributes(AudioAttributes.Builder().setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).setUsage(C.USAGE_MEDIA).build(), true)
            .setHandleAudioBecomingNoisy(true).build()
    }
}
