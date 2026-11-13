package com.sza.streamsplayer.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.sza.streamsplayer.data.local.db.StreamSourceEntity
import com.sza.streamsplayer.data.repository.StreamSourceRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * S0035, CONTEXT-HELP rule 14: replaces the channel list with fictional demo channels so a help screenshot never
 * shows the author's or a user's data. Addresses use the reserved `.invalid` top-level domain, which never
 * resolves. Debug builds only; the capture script drives it on an emulator:
 *
 *     adb shell am broadcast -a com.sza.streamsplayer.debug.SEED_HELP_DEMO --ez wipe true -n <package>/<receiver>
 *
 * Without `--ez wipe true` it refuses, so a stray broadcast cannot empty a real list.
 */
class HelpDemoSeedReceiver : BroadcastReceiver() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Dependencies {
        fun repository(): StreamSourceRepository
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION) return
        if (!intent.getBooleanExtra(EXTRA_WIPE, false)) {
            Log.w(TAG, "refused: the broadcast must carry --ez $EXTRA_WIPE true")
            return
        }
        val pending = goAsync()
        val repository = EntryPointAccessors.fromApplication(context.applicationContext, Dependencies::class.java).repository()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                repository.observeSources().first().forEach { repository.remove(it) }
                val now = System.currentTimeMillis()
                DEMO.forEachIndexed { index, demo ->
                    repository.add(
                        StreamSourceEntity(
                            id = "help-demo-$index",
                            url = demo.url,
                            title = demo.title,
                            mediaKind = demo.kind,
                            sourceOrigin = "MANUAL",
                            sortIndex = index,
                            pinned = demo.pinned,
                            addedAt = now - index * 1000L,
                            category = demo.category,
                        ),
                    )
                    if (demo.played) repository.markPlayed("help-demo-$index", now - index * 60_000L)
                }
                Log.i(TAG, "seeded ${DEMO.size} demo channels")
            } finally {
                pending.finish()
            }
        }
    }

    private data class Demo(
        val title: String,
        val url: String,
        val kind: String,
        val pinned: Boolean,
        val played: Boolean,
        val category: String?,
    )

    companion object {
        const val ACTION = "com.sza.streamsplayer.debug.SEED_HELP_DEMO"
        const val EXTRA_WIPE = "wipe"
        private const val TAG = "HelpDemoSeed"

        private val DEMO = listOf(
            Demo("Harbour Radio", "https://demo.example.invalid/radio/harbour", "AUDIO", pinned = true, played = true, category = "Music"),
            Demo("Night Jazz", "https://demo.example.invalid/radio/night-jazz", "AUDIO", pinned = true, played = false, category = "Music"),
            Demo("Morning News FM", "https://demo.example.invalid/radio/morning-news", "AUDIO", pinned = false, played = true, category = "News"),
            Demo("City Camera", "https://demo.example.invalid/video/city.m3u8", "VIDEO", pinned = false, played = true, category = "Cameras"),
            Demo("Mountain Live", "https://demo.example.invalid/video/mountain.m3u8", "VIDEO", pinned = false, played = false, category = "Cameras"),
            Demo("Garden Cam", "rtsp://demo.example.invalid/garden", "RTSP", pinned = false, played = false, category = "Cameras"),
        )
    }
}
