package com.sza.streamsplayer.data.local.db

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * S0018: Room migration test from schema 1 to schema 2 with pre-existing rows.
 * Validates:
 * - stream_sources gains nullable exchangeEntryId and exchangeBroadcastId
 * - existing rows survive without modification
 * - exchange_entries table is created with unique index on (endpoint, login)
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class StreamsDatabaseMigrationTest {

    @Test
    fun migrate2To3_preservesIdentityAndEncryptedTokenAndDefaultsToMember() {
        val name = "migration-test-streams-2-3.db"
        helper.createDatabase(name, 2).use { db ->
            db.execSQL("INSERT INTO exchange_entries (entryId, endpoint, login, pin, deviceId, deviceName, state, createdAt, tokenCiphertext, tokenIv) VALUES ('retained', 'server.net:443', 'alice', 'SHA256:leaf', 'stable-device', 'Phone', 'Connected', 100, 'encrypted-token', 'iv')")
        }
        helper.runMigrationsAndValidate(name, 3, true, StreamsDatabase.MIGRATION_2_3).use { db ->
            db.query("SELECT entryId, deviceId, pin, tokenCiphertext, tokenIv, accountKind, signInReason, pinnedPublicKey FROM exchange_entries").use { row ->
                assertTrue(row.moveToFirst())
                assertEquals("retained", row.getString(0))
                assertEquals("stable-device", row.getString(1))
                assertEquals("SHA256:leaf", row.getString(2))
                assertEquals("encrypted-token", row.getString(3))
                assertEquals("iv", row.getString(4))
                assertEquals("member", row.getString(5))
                assertTrue(row.isNull(6))
                assertTrue(row.isNull(7))
            }
        }
    }

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        StreamsDatabase::class.java
    )

    @Test
    fun migrate1To2_preservesExistingRowsAndAddsExchangeColumnsAndEntriesTable() {
        val testDbName = "migration-test-streams-1-2.db"

        // 1. Create DB at version 1 and insert a pre-migration stream source
        helper.createDatabase(testDbName, 1).use { db ->
            db.execSQL(
                """
                INSERT INTO stream_sources (
                    id, url, title, mediaKind, sourceOrigin, sortIndex, pinned, addedAt,
                    lastPlayedAt, category, topic, language, country, access,
                    preferredAudioLang, preferredSubtitleLang, subtitlesEnabled, identityKey, sourceDeviceId
                ) VALUES (
                    'source-1', 'http://example.com/live.m3u8', 'Pre-existing Channel', 'VIDEO',
                    'MANUAL', 0, 1, 1000, 2000, 'News', 'World', 'en', 'US', NULL,
                    NULL, NULL, NULL, 'http://example.com/live.m3u8', 'device-xyz'
                )
                """.trimIndent()
            )
        }

        // 2. Migrate to version 2 and validate schema against exported 2.json
        val db = helper.runMigrationsAndValidate(
            testDbName,
            2,
            true,
            StreamsDatabase.MIGRATION_1_2
        )

        // 3. Verify pre-existing stream source survived with exchange fields as null
        db.query("SELECT id, url, title, pinned, sourceDeviceId, exchangeEntryId, exchangeBroadcastId FROM stream_sources WHERE id = 'source-1'").use { cursor ->
            assertTrue("Pre-existing row must survive migration", cursor.moveToFirst())
            assertEquals("source-1", cursor.getString(0))
            assertEquals("http://example.com/live.m3u8", cursor.getString(1))
            assertEquals("Pre-existing Channel", cursor.getString(2))
            assertEquals(1, cursor.getInt(3))
            assertEquals("device-xyz", cursor.getString(4))
            assertNull("exchangeEntryId must be null for pre-migration row", cursor.getString(5))
            assertNull("exchangeBroadcastId must be null for pre-migration row", cursor.getString(6))
        }

        // 4. Verify exchange_entries table was created and can store an entry
        db.execSQL(
            """
            INSERT INTO exchange_entries (
                entryId, endpoint, login, pin, deviceId, deviceName, label, state, createdAt, tokenCiphertext, tokenIv
            ) VALUES (
                'entry-1', 'server.example.net:44022', 'alice', 'SHA256:abc', 'dev-123456789012345678', 'Pixel', 'Home', 'Connected', 12345, 'cipher==', 'iv=='
            )
            """.trimIndent()
        )

        db.query("SELECT entryId, endpoint, login, deviceId, state FROM exchange_entries WHERE entryId = 'entry-1'").use { cursor ->
            assertTrue("exchange_entries row must be inserted", cursor.moveToFirst())
            assertEquals("entry-1", cursor.getString(0))
            assertEquals("server.example.net:44022", cursor.getString(1))
            assertEquals("alice", cursor.getString(2))
            assertEquals("dev-123456789012345678", cursor.getString(3))
            assertEquals("Connected", cursor.getString(4))
        }
    }
}
