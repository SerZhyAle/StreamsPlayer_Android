package com.sza.streamsplayer.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.sza.streamsplayer.data.repository.settings.BroadcastSettingsStore
import com.sza.streamsplayer.domain.model.AppSettings
import com.sza.streamsplayer.domain.model.StreamsCatalogRefreshPolicy
import com.sza.streamsplayer.domain.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepositoryImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val dataStore: DataStore<Preferences>
) : SettingsRepository {

    private val settingsUpdateMutex = Mutex()
    private val transformMutex = Mutex()

    companion object {
        private val KEY_ENABLE_WEAR_COMPANION = booleanPreferencesKey("enable_wear_companion")
        private val KEY_DIM_CLOCK_OVERLAY_ENABLED = booleanPreferencesKey("dim_clock_overlay_enabled")
        private val KEY_CATALOG_REFRESH_POLICY = stringPreferencesKey("streams_catalog_refresh_policy")
    }

    override fun getSettings(): Flow<AppSettings> =
        dataStore.data
            .catch { exception ->
                if (exception is IOException) {
                    Timber.e(exception, "Error reading preferences")
                    emit(emptyPreferences())
                } else {
                    throw exception
                }
            }
            .map { preferences ->
                val broadcastValues = BroadcastSettingsStore.read(preferences, context)
                AppSettings(
                    broadcast = broadcastValues.broadcast,
                    enableWearCompanion = preferences[KEY_ENABLE_WEAR_COMPANION] ?: false,
                    enableBroadcasting = broadcastValues.enableBroadcasting,
                    dimClockOverlayEnabled = preferences[KEY_DIM_CLOCK_OVERLAY_ENABLED] ?: false,
                    streamsCatalogRefreshPolicy = StreamsCatalogRefreshPolicy.fromName(preferences[KEY_CATALOG_REFRESH_POLICY]),
                )
            }

    override suspend fun updateSettings(settings: AppSettings) {
        settingsUpdateMutex.withLock {
            dataStore.edit { preferences ->
                BroadcastSettingsStore.write(preferences, settings)
                preferences[KEY_ENABLE_WEAR_COMPANION] = settings.enableWearCompanion
                preferences[KEY_DIM_CLOCK_OVERLAY_ENABLED] = settings.dimClockOverlayEnabled
                preferences[KEY_CATALOG_REFRESH_POLICY] = settings.streamsCatalogRefreshPolicy.name
            }
        }
    }

    override suspend fun updateSettings(transform: suspend (AppSettings) -> AppSettings) {
        transformMutex.withLock {
            val current = getSettings().first()
            val updated = transform(current)
            updateSettings(updated)
        }
    }
}
