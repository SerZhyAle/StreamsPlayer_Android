package com.sza.streamsplayer.core.di

import android.content.Context
import androidx.room.Room
import com.sza.streamsplayer.data.local.db.ExchangeEntryDao
import com.sza.streamsplayer.data.local.db.StreamCollectionDao
import com.sza.streamsplayer.data.local.db.StreamQualityMemoryDao
import com.sza.streamsplayer.data.local.db.StreamSourceDao
import com.sza.streamsplayer.data.local.db.StreamUserStateDao
import com.sza.streamsplayer.data.local.db.StreamsDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object StreamCoreModule {

    @Provides
    @Singleton
    fun provideStreamsDatabase(
        @ApplicationContext context: Context
    ): StreamsDatabase {
        return Room.databaseBuilder(
            context,
            StreamsDatabase::class.java,
            "streams.db"
        )
            .addMigrations(StreamsDatabase.MIGRATION_1_2, StreamsDatabase.MIGRATION_2_3)
            .build()
    }

    @Provides
    fun provideStreamSourceDao(database: StreamsDatabase): StreamSourceDao =
        database.streamSourceDao()

    @Provides
    fun provideStreamUserStateDao(database: StreamsDatabase): StreamUserStateDao =
        database.streamUserStateDao()

    @Provides
    fun provideStreamQualityMemoryDao(database: StreamsDatabase): StreamQualityMemoryDao =
        database.streamQualityMemoryDao()

    @Provides
    fun provideStreamCollectionDao(database: StreamsDatabase): StreamCollectionDao =
        database.streamCollectionDao()

    @Provides
    fun provideExchangeEntryDao(database: StreamsDatabase): ExchangeEntryDao =
        database.exchangeEntryDao()

    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }
}
