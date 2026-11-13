package com.sza.streamsplayer.wear.data.delivery

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * An in-memory `DataStore<Preferences>` for the delivery tests (S0064). A file-backed store renames a
 * temporary file over the live one on every write, which the JVM refuses on Windows while the previous
 * write still holds the target open - see `WearBroadcastIdentityStoreTest` - so a second write in one test
 * would fail for a reason that has nothing to do with the code under test.
 *
 * [state] is public so a test can hand the same storage to a second instance, which is what an app restart
 * looks like to a store.
 */
internal class InMemoryPreferencesDataStore(
    val state: MutableStateFlow<Preferences> = MutableStateFlow(emptyPreferences())
) : DataStore<Preferences> {

    override val data: Flow<Preferences> = state

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
        val updated = transform(state.value)
        state.value = updated
        return updated
    }
}
