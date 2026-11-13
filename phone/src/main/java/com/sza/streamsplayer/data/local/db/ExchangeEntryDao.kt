package com.sza.streamsplayer.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * Data access for `exchange_entries` table.
 */
@Dao
interface ExchangeEntryDao {

    @Query("SELECT * FROM exchange_entries LIMIT 1")
    suspend fun getEntry(): ExchangeAccountEntryEntity?

    @Query("SELECT * FROM exchange_entries LIMIT 1")
    fun observeEntry(): Flow<ExchangeAccountEntryEntity?>

    @Query("SELECT * FROM exchange_entries WHERE entryId = :entryId LIMIT 1")
    suspend fun getById(entryId: String): ExchangeAccountEntryEntity?

    @Query("SELECT * FROM exchange_entries WHERE endpoint = :endpoint AND login = :login LIMIT 1")
    suspend fun getByIdentity(endpoint: String, login: String): ExchangeAccountEntryEntity?

    @Query("SELECT COUNT(*) FROM exchange_entries")
    suspend fun count(): Int

    @Upsert
    suspend fun upsert(entry: ExchangeAccountEntryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrReplace(entry: ExchangeAccountEntryEntity)

    @Query("UPDATE exchange_entries SET state = :state WHERE entryId = :entryId")
    suspend fun updateState(entryId: String, state: String)

    @Query("UPDATE exchange_entries SET state = :state")
    suspend fun updateAllState(state: String)

    @Query("UPDATE exchange_entries SET tokenCiphertext = :ciphertext, tokenIv = :iv WHERE entryId = :entryId")
    suspend fun updateToken(entryId: String, ciphertext: String?, iv: String?)

    @Query("UPDATE exchange_entries SET pin = :pin, pinnedPublicKey = :key, label = :label, state = :state, deviceName = :deviceName, accountKind = :kind, signInReason = :reason WHERE entryId = :entryId")
    suspend fun updateMetadata(entryId: String, pin: String?, key: String?, label: String?, state: String,
        deviceName: String, kind: String, reason: String?)

    @Query("UPDATE exchange_entries SET pin = :pin, label = :label WHERE entryId = :entryId")
    suspend fun updatePinAndLabel(entryId: String, pin: String?, label: String?)

    @Query("DELETE FROM exchange_entries")
    suspend fun deleteAll()

    @Query("DELETE FROM exchange_entries WHERE entryId = :entryId")
    suspend fun deleteById(entryId: String)
}
