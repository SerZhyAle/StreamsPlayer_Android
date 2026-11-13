package com.sza.streamsplayer.wear.data.delivery

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.sza.streamsplayer.wear.data.preferences.WearPreferenceKeys
import com.sza.streamsplayer.wear.domain.delivery.DeliverableSet
import kotlinx.coroutines.flow.first

/**
 * S0064: the channel-picture sets the wearer removed on purpose.
 *
 * The watch re-fetches any absent set on its next pull to refresh, because an absent set always differs
 * from the manifest. Without this record a removal would be undone by the wearer's own next refresh, so
 * Remove writes the set here, [WearArtworkDelivery.refresh] skips it, and Download clears it again.
 * There is no source twin for this: the phone has an offer the user answers, the watch has none.
 *
 * Takes the store rather than `WearSettingsDataStore` for the reason `WearBroadcastIdentityStore` gives:
 * the wrapper rules out binding `DataStore<Preferences>` into the graph, so the wiring is a `@Provides`.
 */
class WearArtworkOptOutStore(
    private val store: DataStore<Preferences>
) {

    /** Every set currently removed on purpose; a name this build does not know is ignored, not an error. */
    suspend fun optedOut(): Set<DeliverableSet> {
        val names = store.data.first()[WearPreferenceKeys.ARTWORK_NOT_FETCHED].orEmpty()
        return DeliverableSet.values().filterTo(HashSet()) { it.name in names }
    }

    suspend fun isOptedOut(set: DeliverableSet): Boolean = set in optedOut()

    /**
     * Records or clears the removal of [set]. The edit runs inside one transaction, so two sets changed
     * back to back cannot overwrite each other's name; names of other sets, known or not, stay as they are.
     */
    suspend fun setOptedOut(set: DeliverableSet, removed: Boolean) {
        store.edit { prefs ->
            val names = prefs[WearPreferenceKeys.ARTWORK_NOT_FETCHED].orEmpty()
            val updated = if (removed) names + set.name else names - set.name
            if (updated.isEmpty()) {
                prefs.remove(WearPreferenceKeys.ARTWORK_NOT_FETCHED)
            } else {
                prefs[WearPreferenceKeys.ARTWORK_NOT_FETCHED] = updated
            }
        }
    }
}
