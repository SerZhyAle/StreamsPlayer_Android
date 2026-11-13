package com.sza.streamsplayer.data.local.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.sza.streamsplayer.data.exchange.ExchangeAccountEntry
import com.sza.streamsplayer.data.exchange.ExchangeAccountState

/**
 * S0018: Room entity for exchange account entries table `exchange_entries`.
 *
 * This product holds at most one account entry (Ask D, owner ruling 2026-10-10).
 * Device token ciphertext and IV are kept beside the entry id (Scope 4).
 * Password is never persisted.
 */
@Entity(
    tableName = "exchange_entries",
    indices = [
        Index(value = ["endpoint", "login"], unique = true, name = "index_exchange_entries_endpoint_login")
    ]
)
data class ExchangeAccountEntryEntity(
    @PrimaryKey
    val entryId: String,
    val endpoint: String,
    val login: String,
    val pin: String? = null,
    val deviceId: String,
    val deviceName: String,
    val label: String? = null,
    val state: String,
    val createdAt: Long,
    val tokenCiphertext: String? = null,
    val tokenIv: String? = null,
    @ColumnInfo(defaultValue = "'member'") val accountKind: String = "member",
    val signInReason: String? = null,
    val pinnedPublicKey: String? = null,
) {
    fun toModel(): ExchangeAccountEntry = ExchangeAccountEntry(
        entryId = entryId,
        endpoint = endpoint,
        login = login,
        pin = pin,
        deviceId = deviceId,
        deviceName = deviceName,
        label = label,
        state = ExchangeAccountState.fromString(state),
        createdAt = createdAt,
        accountKind = accountKind,
        signInReason = signInReason,
        pinnedPublicKey = pinnedPublicKey,
    )

    companion object {
        fun fromModel(
            entry: ExchangeAccountEntry,
            tokenCiphertext: String? = null,
            tokenIv: String? = null,
        ): ExchangeAccountEntryEntity = ExchangeAccountEntryEntity(
            entryId = entry.entryId,
            endpoint = entry.endpoint,
            login = entry.login,
            pin = entry.pin,
            deviceId = entry.deviceId,
            deviceName = entry.deviceName,
            label = entry.label,
            state = entry.state.name,
            createdAt = entry.createdAt,
            tokenCiphertext = tokenCiphertext,
            tokenIv = tokenIv,
            accountKind = entry.accountKind,
            signInReason = entry.signInReason,
            pinnedPublicKey = entry.pinnedPublicKey,
        )
    }
}
