package com.sza.streamsplayer.data.repository.settings

import android.content.Context
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S0659: the last catalog-refresh timestamp of the catalog screen. This is ephemeral session memory, NOT a
 * user setting, so it lives in its own DataStore file ("streams_session") and is intentionally kept out of
 * the main settings DataStore / [com.sza.streamsplayer.domain.model.AppSettings] (research H concern).
 *
 * S4459: [Session.catalogInstalledStamp] is the publication stamp of the catalog archive the last import
 * installed, and [Session.catalogDismissedStamp] the stamp whose update offer the user closed; the update
 * banner is offered only for a published stamp that equals neither.
 *
 * S0068: the source's sort, filter, display-mode and scroll keys are not carried - nothing on this
 * product's catalog screen reads them.
 */
private val Context.streamsSessionDataStore by preferencesDataStore(
    name = "streams_session",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

@Singleton
class StreamsSessionStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    /** Snapshot of the last session. [lastCatalogRefreshAt] is epoch millis, 0 = never. */
    data class Session(
        val lastCatalogRefreshAt: Long,
        val catalogInstalledStamp: String? = null,
        val catalogDismissedStamp: String? = null,
    )

    suspend fun read(): Session {
        val prefs = context.streamsSessionDataStore.data.first()
        return Session(
            lastCatalogRefreshAt = prefs[KEY_LAST_CATALOG_REFRESH_AT] ?: 0L,
            catalogInstalledStamp = prefs[KEY_CATALOG_INSTALLED_STAMP],
            catalogDismissedStamp = prefs[KEY_CATALOG_DISMISSED_STAMP],
        )
    }

    suspend fun writeCatalogRefreshAt(epochMillis: Long) {
        context.streamsSessionDataStore.edit { prefs ->
            prefs[KEY_LAST_CATALOG_REFRESH_AT] = epochMillis
        }
    }

    /**
     * S4459: the publication stamp of the archive the last successful import installed. Null removes the
     * key, so an import whose response carried no usable header cannot leave the previous stamp standing
     * as a claim about content that has since been replaced.
     */
    suspend fun writeCatalogInstalledStamp(stamp: String?) {
        context.streamsSessionDataStore.edit { prefs ->
            if (stamp != null) {
                prefs[KEY_CATALOG_INSTALLED_STAMP] = stamp
            } else {
                prefs.remove(KEY_CATALOG_INSTALLED_STAMP)
            }
        }
    }

    /** S4459: the publication stamp whose update offer the user closed; a different stamp is offered again. */
    suspend fun writeCatalogDismissedStamp(stamp: String) {
        context.streamsSessionDataStore.edit { prefs ->
            prefs[KEY_CATALOG_DISMISSED_STAMP] = stamp
        }
    }

    private companion object {
        val KEY_LAST_CATALOG_REFRESH_AT = longPreferencesKey("last_catalog_refresh_at")
        val KEY_CATALOG_INSTALLED_STAMP = stringPreferencesKey("catalog_installed_stamp")
        val KEY_CATALOG_DISMISSED_STAMP = stringPreferencesKey("catalog_dismissed_stamp")
    }
}
