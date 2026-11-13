package com.sza.streamsplayer.ui.streams

import com.sza.streamsplayer.data.local.db.StreamSourceEntity
import com.sza.streamsplayer.ui.streams.own.LiveBroadcast
import com.sza.streamsplayer.ui.streams.own.LivePresence

/**
 * One row of the streams list. Every category is a list of [Channel]; the Own category (S0038) also carries
 * section headers, device headers and live rows. [key] is stable across updates so a background change does not
 * move focus from the row a person is on.
 */
sealed interface StreamListRow {
    val key: String

    data class Channel(val source: StreamSourceEntity) : StreamListRow {
        override val key: String get() = "channel:${source.id}"
    }

    enum class SectionKind { LIVE, SAVED }

    data class Section(val kind: SectionKind) : StreamListRow {
        override val key: String get() = "section:$kind"
    }

    data class Device(val deviceId: String, val name: String?, val presence: LivePresence) : StreamListRow {
        override val key: String get() = "device:$deviceId"
    }

    data class Live(val broadcast: LiveBroadcast) : StreamListRow {
        override val key: String get() = "live:${broadcast.entryId}:${broadcast.broadcastId}"
    }

    /** The live group is shown, loaded and has nothing in it. */
    data object LiveEmpty : StreamListRow {
        override val key: String get() = "live-empty"
    }
}
