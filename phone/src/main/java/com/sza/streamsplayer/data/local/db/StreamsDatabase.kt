package com.sza.streamsplayer.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * StreamsPlayer on-device Room database.
 *
 * Version 1 contains stream subsystem entities:
 * - [StreamSourceEntity]
 * - [StreamUserStateEntity]
 * - [StreamQualityMemoryEntity]
 * - [StreamCollectionEntity]
 * - [StreamCollectionMemberEntity]
 *
 * Version 2 (S0018):
 * - [StreamSourceEntity] gains nullable `exchangeEntryId` and `exchangeBroadcastId`
 * - [ExchangeAccountEntryEntity] (`exchange_entries` table)
 */
@Database(
    entities = [
        StreamSourceEntity::class,
        StreamUserStateEntity::class,
        StreamQualityMemoryEntity::class,
        StreamCollectionEntity::class,
        StreamCollectionMemberEntity::class,
        ExchangeAccountEntryEntity::class,
    ],
    version = 3,
    exportSchema = true
)
abstract class StreamsDatabase : RoomDatabase() {
    abstract fun streamSourceDao(): StreamSourceDao
    abstract fun streamUserStateDao(): StreamUserStateDao
    abstract fun streamQualityMemoryDao(): StreamQualityMemoryDao
    abstract fun streamCollectionDao(): StreamCollectionDao
    abstract fun exchangeEntryDao(): ExchangeEntryDao

    companion object {
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `exchange_entries` ADD COLUMN `accountKind` TEXT NOT NULL DEFAULT 'member'")
                db.execSQL("ALTER TABLE `exchange_entries` ADD COLUMN `signInReason` TEXT")
                db.execSQL("ALTER TABLE `exchange_entries` ADD COLUMN `pinnedPublicKey` TEXT")
            }
        }

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `stream_sources` ADD COLUMN `exchangeEntryId` TEXT")
                db.execSQL("ALTER TABLE `stream_sources` ADD COLUMN `exchangeBroadcastId` TEXT")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_stream_sources_exchangeEntryId` ON `stream_sources` (`exchangeEntryId`)")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `exchange_entries` (
                        `entryId` TEXT NOT NULL,
                        `endpoint` TEXT NOT NULL,
                        `login` TEXT NOT NULL,
                        `pin` TEXT,
                        `deviceId` TEXT NOT NULL,
                        `deviceName` TEXT NOT NULL,
                        `label` TEXT,
                        `state` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `tokenCiphertext` TEXT,
                        `tokenIv` TEXT,
                        PRIMARY KEY(`entryId`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_exchange_entries_endpoint_login` ON `exchange_entries` (`endpoint`, `login`)")
            }
        }
    }
}
