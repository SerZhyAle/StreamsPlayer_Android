package com.sza.streamsplayer.domain.repository

import com.sza.streamsplayer.domain.model.AppSettings
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    fun getSettings(): Flow<AppSettings>
    suspend fun updateSettings(settings: AppSettings)
    suspend fun updateSettings(transform: suspend (AppSettings) -> AppSettings)
}
