package com.sza.streamsplayer.testing

import android.content.Context
import androidx.room.Room
import com.sza.streamsplayer.data.local.db.StreamsDatabase
import org.junit.rules.ExternalResource

/**
 * Test helpers for spinning up an in-memory [StreamsDatabase].
 *
 * [StreamsDatabase] declares `@TypeConverters(Converters::class)` and `Converters` has a no-arg
 * constructor, so Room instantiates it reflectively - no `.addTypeConverter(..)` is needed and the
 * plain builder is sufficient. Should `Converters` ever gain constructor args, add an overload here
 * that takes a pre-built instance and calls `.addTypeConverter(converters)` on the builder.
 *
 * The builder uses [Room.inMemoryDatabaseBuilder] with [androidx.room.RoomDatabase.Builder.allowMainThreadQueries]
 * so synchronous unit tests can query without an executor hop. The caller supplies a [Context]
 * (typically a Robolectric `ApplicationProvider.getApplicationContext()`); this file itself does not
 * depend on a running Robolectric environment.
 */
fun createInMemoryAppDatabase(context: Context): StreamsDatabase =
    Room.inMemoryDatabaseBuilder(context, StreamsDatabase::class.java)
        .allowMainThreadQueries()
        .build()

/**
 * JUnit `@get:Rule`-friendly wrapper that builds an in-memory [StreamsDatabase] before each test and
 * closes it afterwards, so individual cases never leak a database instance.
 *
 * Usage:
 * ```
 * @get:Rule val dbRule = InMemoryRoomRule { ApplicationProvider.getApplicationContext() }
 * // ... dbRule.db.someDao() ...
 * ```
 *
 * The [contextProvider] is invoked lazily inside [before], so the Robolectric application context is
 * resolved only when the test actually runs.
 */
class InMemoryRoomRule(
    private val contextProvider: () -> Context,
) : ExternalResource() {

    lateinit var db: StreamsDatabase
        private set

    override fun before() {
        db = createInMemoryAppDatabase(contextProvider())
    }

    override fun after() {
        if (::db.isInitialized && db.isOpen) {
            db.close()
        }
    }
}
