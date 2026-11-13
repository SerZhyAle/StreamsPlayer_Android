package com.sza.streamsplayer.ui.streams

import android.os.Looper
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AlertDialog
import androidx.media3.common.Player
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import com.sza.streamsplayer.R
import com.sza.streamsplayer.data.local.db.StreamSourceEntity
import com.sza.streamsplayer.ui.player.helpers.AudioServiceController
import com.sza.streamsplayer.ui.streams.helpers.StreamInlineAudioManager
import com.sza.streamsplayer.ui.streams.helpers.StreamInlineAudioViews
import com.sza.streamsplayer.ui.streams.helpers.StreamInlineAudioCallbacks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlertDialog

@UnstableApi
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class StreamShellLifecycleTest {
    class HostActivity : AppCompatActivity() {
        override fun onCreate(savedInstanceState: android.os.Bundle?) {
            setTheme(R.style.Theme_StreamsPlayer)
            super.onCreate(savedInstanceState)
        }
    }

    private fun channel() = StreamSourceEntity(id = "radio", url = "https://host/live", title = "Radio",
        mediaKind = "AUDIO", sourceOrigin = "MANUAL", sortIndex = 0, addedAt = 0)

    @Test
    fun channelSheetSurvivesRecreationAndStillDeliversActions() {
        val controller = Robolectric.buildActivity(HostActivity::class.java).setup()
        StreamChannelActionsSheet.newInstance(channel()).showNow(controller.get().supportFragmentManager, StreamChannelActionsSheet.TAG)
        controller.recreate()
        val host = controller.get()
        var action: String? = null
        host.supportFragmentManager.setFragmentResultListener(StreamChannelActionsSheet.RESULT, host) { _, result ->
            action = result.getString(StreamChannelActionsSheet.ACTION)
        }
        val sheet = host.supportFragmentManager.findFragmentByTag(StreamChannelActionsSheet.TAG)!!
        sheet.requireView().findViewById<android.view.View>(R.id.actionPlay).performClick()
        assertEquals(StreamChannelActionsSheet.PLAY, action)
        controller.pause().stop().destroy()
    }

    @Test
    fun invalidManualUrlKeepsDialogOpenUntilCorrected() {
        val controller = Robolectric.buildActivity(HostActivity::class.java).setup()
        var savedUrl: String? = null
        StreamEditDialog.show(controller.get()) { url, _, _, _ -> savedUrl = url }
        shadowOf(Looper.getMainLooper()).idle()
        val dialog = ShadowAlertDialog.getLatestDialog() as AlertDialog
        val url = dialog.findViewById<android.widget.EditText>(R.id.etStreamUrl)!!
        url.setText("https://")
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        assertTrue(dialog.isShowing)
        assertNotNull(url.error)
        assertNull(savedUrl)
        url.setText("https://host/live")
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        assertEquals("https://host/live", savedUrl)
        assertFalse(dialog.isShowing)
        controller.pause().stop().destroy()
    }

    @Test
    fun releasingScreenDetachesWithoutStoppingBackgroundRadio() {
        val controller = Robolectric.buildActivity(HostActivity::class.java).setup()
        val host = controller.get()
        val serviceController = mockk<AudioServiceController>(relaxed = true)
        val player = mockk<Player>(relaxed = true)
        every { player.mediaMetadata } returns MediaMetadata.EMPTY
        every { player.playWhenReady } returns true
        every { player.isPlaying } returns true
        val bar = FrameLayout(host)
        val title = TextView(host)
        val manager = StreamInlineAudioManager(host, StreamInlineAudioViews(bar, title, ImageButton(host)), serviceController)
        manager.attach(channel(), player)
        assertEquals("Radio", title.text.toString())
        manager.release()
        verify { player.removeListener(any()) }
        verify(exactly = 0) { player.stop() }
        verify(exactly = 0) { player.clearMediaItems() }
        verify { serviceController.release() }
        shadowOf(Looper.getMainLooper()).idle()
        controller.pause().stop().destroy()
    }

    @Test
    fun exhaustedServiceSessionHidesBarAndReportsFailure() {
        val controller = Robolectric.buildActivity(HostActivity::class.java).setup()
        val host = controller.get()
        val serviceController = mockk<AudioServiceController>(relaxed = true)
        val player = mockk<Player>(relaxed = true)
        val listener = io.mockk.slot<Player.Listener>()
        every { player.addListener(capture(listener)) } returns Unit
        every { player.mediaMetadata } returns MediaMetadata.EMPTY
        val bar = FrameLayout(host)
        val source = channel()
        var failed: StreamSourceEntity? = null
        val manager = StreamInlineAudioManager(
            host, StreamInlineAudioViews(bar, TextView(host), ImageButton(host)), serviceController,
            StreamInlineAudioCallbacks(onError = { failed = it }),
        )
        manager.attach(source, player)
        listener.captured.onMediaItemTransition(null, Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED)
        assertEquals(android.view.View.GONE, bar.visibility)
        assertNull(manager.playingId)
        assertEquals(source, failed)
        verify { player.removeListener(any()) }
        manager.release()
        controller.pause().stop().destroy()
    }

    @Test
    fun attachingToPlayingServiceDoesNotRecordAnotherPlay() {
        val controller = Robolectric.buildActivity(HostActivity::class.java).setup()
        val host = controller.get()
        val serviceController = mockk<AudioServiceController>(relaxed = true)
        val player = mockk<Player>(relaxed = true)
        every { player.mediaMetadata } returns MediaMetadata.EMPTY
        every { player.playWhenReady } returns true
        every { player.isPlaying } returns true
        var successes = 0
        repeat(2) {
            val manager = StreamInlineAudioManager(
                host, StreamInlineAudioViews(FrameLayout(host), TextView(host), ImageButton(host)), serviceController,
                StreamInlineAudioCallbacks(onSuccess = { successes++ }),
            )
            manager.attach(channel(), player)
            manager.release()
        }
        assertEquals(0, successes)
        controller.pause().stop().destroy()
    }
}
