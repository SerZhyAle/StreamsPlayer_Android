package com.sza.streamsplayer.ui.exchange

import android.app.Activity
import android.app.AlertDialog
import android.app.Application
import android.content.Context
import android.os.Looper
import com.google.gson.JsonParser
import com.sza.streamsplayer.data.broadcast.BroadcastDescriptorDto
import com.sza.streamsplayer.data.broadcast.BroadcastDescriptorSerializer
import com.sza.streamsplayer.data.broadcast.BroadcastDescriptorStore
import com.sza.streamsplayer.data.exchange.DeviceExchangeEnvelope
import com.sza.streamsplayer.data.exchange.InMemoryEntryStore
import com.sza.streamsplayer.data.exchange.session.DeviceExchangeControlStream
import com.sza.streamsplayer.ui.player.FullscreenPlayerActivity
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlertDialog

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ExchangeReceiverRuntimeTest {
    private val control = mockk<DeviceExchangeControlStream>(relaxed = true)
    private lateinit var runtime: ExchangeReceiverRuntime
    private lateinit var descriptors: BroadcastDescriptorStore

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val app = RuntimeEnvironment.getApplication() as Application
        app.getSharedPreferences("exchange_receiver", Context.MODE_PRIVATE).edit().clear().commit()
        descriptors = BroadcastDescriptorStore(app)
        runtime = ExchangeReceiverRuntime(app, InMemoryEntryStore(), control, descriptors)
        runtime.start(app)
    }
    @After fun tearDown() { Dispatchers.resetMain() }

    private fun offer(id: String = "test-broadcast") = DeviceExchangeEnvelope.of(DeviceExchangeEnvelope.TYPE_CAST_OFFER) {
        addProperty("fromDeviceId", "sender")
        add("broadcast", com.google.gson.JsonObject().apply {
            addProperty("broadcastId", id)
            add("descriptor", JsonParser.parseString(BroadcastDescriptorSerializer().serialize(
                BroadcastDescriptorDto(url = "http://localhost/live.aac", mode = "AUDIO_ONLY", title = "Live"))))
        })
    }

    @Test fun `cast asks by default and acceptance persists descriptor before launching player`() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        assertFalse(runtime.playImmediately)
        runtime.onEnvelope(offer())
        val dialog = ShadowAlertDialog.getLatestAlertDialog()
        assertNotNull(dialog)
        assertNull(shadowOf(activity).nextStartedActivity)
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        shadowOf(Looper.getMainLooper()).idle()
        val intent = shadowOf(activity).nextStartedActivity
        assertEquals(FullscreenPlayerActivity::class.java.name, intent.component?.className)
        assertEquals("test-broadcast", intent.getStringExtra(ExchangeReceiverRuntime.EXTRA_CAST_ID))
        assertNotNull(descriptors.getByUrl("http://localhost/live.aac"))
    }

    @Test fun `cast stop while consent is pending dismisses dialog and never launches playback`() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        runtime.onEnvelope(offer())
        val dialog = ShadowAlertDialog.getLatestAlertDialog()
        runtime.onEnvelope(DeviceExchangeEnvelope.of(DeviceExchangeEnvelope.TYPE_CAST_STOP) {
            addProperty("broadcastId", "test-broadcast")
        })
        shadowOf(Looper.getMainLooper()).idle()
        assertFalse(dialog.isShowing)
        assertNull(shadowOf(activity).nextStartedActivity)
        coVerify(exactly = 0) { control.post(match { it.type == "cast-answer" && it.boolean("accepted") == true }) }
    }

    @Test fun `immediate cast setting persists and broadcast removal finishes only its player`() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        runtime.playImmediately = true
        runtime.onEnvelope(offer())
        val intent = shadowOf(activity).nextStartedActivity
        assertNotNull(intent)
        val player = Robolectric.buildActivity(Activity::class.java, intent).setup().get()
        runtime.onEnvelope(DeviceExchangeEnvelope.of(DeviceExchangeEnvelope.TYPE_CHANGED) {
            add("removals", com.google.gson.JsonObject().apply {
                add("broadcastIds", com.google.gson.JsonArray().apply { add("test-broadcast") })
            })
        })
        assertTrue(player.isFinishing)
        assertFalse(activity.isFinishing)
        assertTrue(runtime.playImmediately)
    }
}
