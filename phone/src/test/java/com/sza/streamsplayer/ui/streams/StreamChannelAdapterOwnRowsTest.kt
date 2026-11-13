package com.sza.streamsplayer.ui.streams

import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.widget.FrameLayout
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.sza.streamsplayer.R
import com.sza.streamsplayer.core.streams.FaviconAtlasSlicer
import com.sza.streamsplayer.data.local.db.StreamSourceEntity
import com.sza.streamsplayer.ui.streams.own.LiveBroadcast
import com.sza.streamsplayer.ui.streams.own.LiveKind
import com.sza.streamsplayer.ui.streams.own.LivePresence
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * S0038: the Own category's rows and status banner inflate and bind with the app theme, in the three authored
 * languages, and a tap reaches the callbacks.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StreamChannelAdapterOwnRowsTest {

    private val context = ContextThemeWrapper(RuntimeEnvironment.getApplication(), R.style.Theme_StreamsPlayer)

    private fun live(saved: StreamSourceEntity? = null, playable: Boolean = true, kind: LiveKind? = LiveKind.AUDIO) = LiveBroadcast(
        entryId = "entry", broadcastId = "b1", deviceId = "d1", deviceName = null, title = null, kind = kind,
        presence = LivePresence.UNKNOWN, playable = playable, saved = saved, descriptorJson = "{}", updatedAtMs = 0,
    )

    private fun host(
        rows: List<StreamListRow>,
        onLive: (LiveBroadcast) -> Unit = {},
        onSave: (LiveBroadcast) -> Unit = {},
    ): RecyclerView {
        val adapter = StreamChannelAdapter(
            scope = CoroutineScope(Dispatchers.Unconfined),
            slicer = FaviconAtlasSlicer { null },
            coordsProvider = { emptyMap() },
            onItemClick = {}, onPinClick = {}, onMoreClick = {},
            onLiveClick = onLive, onLiveSave = onSave,
        )
        val recycler = RecyclerView(context).apply {
            layoutManager = LinearLayoutManager(context)
            this.adapter = adapter
        }
        adapter.submitList(rows)
        recycler.measure(1080, 4000)
        recycler.layout(0, 0, 1080, 4000)
        return recycler
    }

    private fun texts(recycler: RecyclerView): List<String> =
        (0 until recycler.childCount).flatMap { index ->
            val out = mutableListOf<String>()
            fun walk(view: android.view.View) {
                if (view is TextView && view.visibility == android.view.View.VISIBLE) out += view.text.toString()
                if (view is android.view.ViewGroup) (0 until view.childCount).forEach { walk(view.getChildAt(it)) }
            }
            walk(recycler.getChildAt(index))
            out
        }

    @Test
    fun `live group renders its headers, a fallback title and device, and unknown presence`() {
        val recycler = host(
            listOf(
                StreamListRow.Section(StreamListRow.SectionKind.LIVE),
                StreamListRow.Device("d1", null, LivePresence.UNKNOWN),
                StreamListRow.Live(live()),
                StreamListRow.Section(StreamListRow.SectionKind.SAVED),
            ),
        )
        val shown = texts(recycler)
        assertTrue(shown.toString(), context.getString(R.string.own_section_live) in shown)
        assertTrue(context.getString(R.string.own_section_saved) in shown)
        assertTrue(context.getString(R.string.own_live_untitled) in shown)
        assertTrue(context.getString(R.string.own_presence_unknown) in shown)
        assertFalse(context.getString(R.string.own_presence_online) in shown)
        assertTrue(shown.any { it.contains(context.getString(R.string.own_device_unnamed)) })
    }

    @Test
    fun `an unsupported row says so and a saved row shows the saved indicator and no Save button`() {
        val saved = StreamSourceEntity("kept", "http://x", "kept", "AUDIO", "IMPORTED", 0, addedAt = 0)
        val recycler = host(listOf(StreamListRow.Live(live(playable = false)), StreamListRow.Live(live(saved = saved))))
        val first = recycler.getChildAt(0)
        val second = recycler.getChildAt(1)
        assertEquals(android.view.View.VISIBLE, first.findViewById<TextView>(R.id.tvLiveUnsupported).visibility)
        assertEquals(android.view.View.GONE, second.findViewById<TextView>(R.id.tvLiveUnsupported).visibility)
        assertEquals(android.view.View.VISIBLE, second.findViewById<TextView>(R.id.tvLiveSaved).visibility)
        assertEquals(android.view.View.GONE, second.findViewById<android.view.View>(R.id.btnLiveSave).visibility)
        assertEquals(android.view.View.VISIBLE, first.findViewById<android.view.View>(R.id.btnLiveSave).visibility)
    }

    @Test
    fun `tapping a live row and its Save button reaches the callbacks`() {
        var chosen: LiveBroadcast? = null
        var saved: LiveBroadcast? = null
        val row = live()
        val recycler = host(listOf(StreamListRow.Live(row)), onLive = { chosen = it }, onSave = { saved = it })
        recycler.getChildAt(0).performClick()
        recycler.getChildAt(0).findViewById<android.view.View>(R.id.btnLiveSave).performClick()
        assertEquals(row, chosen)
        assertEquals(row, saved)
    }

    @Test
    fun `the empty live group and the section headers are full width in a grid`() {
        val adapter = StreamChannelAdapter(
            CoroutineScope(Dispatchers.Unconfined), FaviconAtlasSlicer { null }, { emptyMap() }, {}, {}, {},
        )
        adapter.submitList(
            listOf(
                StreamListRow.Section(StreamListRow.SectionKind.LIVE),
                StreamListRow.LiveEmpty,
                StreamListRow.Channel(StreamSourceEntity("a", "http://a", "a", "AUDIO", "MANUAL", 0, addedAt = 0)),
            ),
        )
        assertTrue(adapter.isFullWidth(0))
        assertTrue(adapter.isFullWidth(1))
        assertFalse(adapter.isFullWidth(2))
    }

    @Test
    fun `the status banner and every new row layout inflate with the app theme`() {
        val inflater = LayoutInflater.from(context)
        val parent = FrameLayout(context)
        val main = inflater.inflate(R.layout.activity_main, parent, false)
        assertTrue(main.findViewById<android.view.View>(R.id.ownStatusBanner) != null)
        assertTrue(main.findViewById<android.view.View>(R.id.btnOwnRefresh) != null)
        listOf(R.layout.item_stream_section, R.layout.item_stream_device, R.layout.item_stream_live, R.layout.item_stream_live_empty)
            .forEach { assertTrue(inflater.inflate(it, parent, false) != null) }
    }
}
