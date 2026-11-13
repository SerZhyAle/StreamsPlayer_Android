package com.sza.streamsplayer.ui.streams.helpers

import android.content.Intent
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.sza.streamsplayer.R
import com.sza.streamsplayer.data.broadcast.BroadcastDescriptorParser
import com.sza.streamsplayer.data.broadcast.BroadcastDescriptorStore
import com.sza.streamsplayer.ui.player.FullscreenPlayerActivity
import javax.inject.Inject

/** A received link plays temporarily; only an explicit import keeps a channel and its password. */
class BroadcastLinkOpenManager @Inject constructor(
    private val parser: BroadcastDescriptorParser,
    private val descriptors: BroadcastDescriptorStore,
) {
    fun open(activity: AppCompatActivity, raw: String) {
        val outcome = parser.parseDetailed(raw)
        if (outcome !is BroadcastDescriptorParser.ParseOutcome.Valid) {
            Toast.makeText(activity, if (outcome == BroadcastDescriptorParser.ParseOutcome.UnsupportedVersion)
                R.string.import_broadcast_unsupported_version else R.string.import_broadcast_invalid, Toast.LENGTH_LONG).show()
            return
        }
        val dto = outcome.dto
        if (dto.auth?.isPasswordV1 == false) {
            Toast.makeText(activity, R.string.broadcast_auth_unsupported, Toast.LENGTH_LONG).show()
            return
        }
        descriptors.saveDescriptor(dto.url, dto.sourceId, dto)
        activity.startActivity(Intent(activity, FullscreenPlayerActivity::class.java).apply {
            putExtra(FullscreenPlayerActivity.EXTRA_CHANNEL_URL, dto.url)
            putExtra(FullscreenPlayerActivity.EXTRA_CHANNEL_TITLE, dto.title.orEmpty())
        })
    }
}
