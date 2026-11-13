package com.sza.streamsplayer.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.sza.streamsplayer.MainActivity
import com.sza.streamsplayer.ui.streams.helpers.StreamHeadlessPlayManager
import com.sza.streamsplayer.ui.streams.helpers.StreamShortcutRouteManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * S1471 - transparent, no-UI trampoline a pinned stream shortcut or widget targets.
 * Decides "play silently or open the Streams screen" before a single view exists.
 */
@AndroidEntryPoint
class StreamPlayLaunchActivity : AppCompatActivity() {

    @Inject lateinit var routeManager: StreamShortcutRouteManager

    @Inject lateinit var headlessPlay: StreamHeadlessPlayManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val url = intent?.getStringExtra(EXTRA_STREAM_URL)
        if (url.isNullOrBlank()) {
            Timber.w("StreamPlayLaunchActivity: started without a stream url")
            finish()
            return
        }
        lifecycleScope.launch { route(url) }
    }

    private suspend fun route(url: String) {
        val source = routeManager.headlessSource(url)
        if (source == null || !headlessPlay.play(source)) {
            val playIntent = Intent(this, MainActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                data = Uri.parse(url)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            startActivity(playIntent)
        }
        finish()
    }

    companion object {
        const val EXTRA_STREAM_URL = "extra_stream_url"
        const val ACTION_PLAY_STREAM_HEADLESS = "com.sza.streamsplayer.action.PLAY_STREAM_HEADLESS"

        fun createIntent(context: Context, url: String): Intent =
            Intent(context, StreamPlayLaunchActivity::class.java).apply {
                action = ACTION_PLAY_STREAM_HEADLESS
                putExtra(EXTRA_STREAM_URL, url)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
    }
}
