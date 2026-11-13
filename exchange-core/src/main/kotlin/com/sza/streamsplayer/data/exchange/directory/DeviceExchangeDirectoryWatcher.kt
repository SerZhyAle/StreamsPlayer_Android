package com.sza.streamsplayer.data.exchange.directory

import com.sza.streamsplayer.data.exchange.DeviceExchangeEnvelope
import com.sza.streamsplayer.data.exchange.session.DeviceExchangeControlStream
import com.sza.streamsplayer.data.exchange.session.DeviceExchangeRefusedException
import com.sza.streamsplayer.data.exchange.session.DeviceExchangeWelcome
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import java.io.IOException

/**
 * Port of the donor's list/subscribe/revision loop. It observes the existing control stream here:
 * opening a second control stream would replace the publisher's stream (DEVICE-EXCHANGE 7.3).
 */
class DeviceExchangeDirectoryWatcher(private val control: DeviceExchangeControlStream) : DirectorySource {
    /** The snapshots alone; a closed stream reads as an empty directory. */
    fun watch(): Flow<DirectorySnapshot> = observe().map { update ->
        when (update) {
            is DirectoryUpdate.Snapshot -> update.directory
            DirectoryUpdate.Disconnected -> DirectorySnapshot(0, emptyList(), emptyList(), emptyList())
        }
    }

    override suspend fun requestList(): Boolean = control.post(LIST)

    override fun observe(): Flow<DirectoryUpdate> = flow {
        val events = Channel<Event>(Channel.BUFFERED)
        fun offer(event: Event) {
            if (events.trySend(event).isFailure) events.close(IOException("Directory updates overflowed"))
        }
        val listener = object : DeviceExchangeControlStream.Listener {
            override fun onSessionOpened(welcome: DeviceExchangeWelcome) = offer(Event.Open(welcome))
            override fun onSessionClosed() = offer(Event.Closed)
            override fun onEnvelope(envelope: DeviceExchangeEnvelope) {
                if (envelope.type in DIRECTORY_TYPES) offer(Event.Envelope(envelope))
            }
        }
        control.addListener(listener)
        var welcome: DeviceExchangeWelcome? = null
        var held: DeviceExchangeDirectoryState? = null
        val codec = DeviceExchangeDirectoryClient()
        try {
            control.currentWelcome()?.let { offer(Event.Open(it)) }
            for (event in events) {
                when (event) {
                    is Event.Open -> {
                        if (welcome === event.welcome) continue
                        welcome = event.welcome
                        held = null
                        if (control.post(LIST)) control.post(SUBSCRIBE)
                    }
                    Event.Closed -> {
                        welcome = null
                        held = null
                        emit(DirectoryUpdate.Disconnected)
                    }
                    is Event.Envelope -> when (event.value.type) {
                        DeviceExchangeEnvelope.TYPE_DIRECTORY -> {
                            val snapshot = codec.parseSnapshot(event.value)
                            // A delayed answer older than what is held must not bring an ended broadcast back.
                            if (held?.let { snapshot.revision < it.revision } == true) continue
                            held = DeviceExchangeDirectoryState.of(snapshot)
                            emit(DirectoryUpdate.Snapshot(snapshot))
                        }
                        DeviceExchangeEnvelope.TYPE_CHANGED -> {
                            val current = held ?: continue
                            when (val result = current.apply(codec.handleChanged(event.value))) {
                                is DeviceExchangeDirectoryState.Outcome.Applied -> {
                                    held = result.state
                                    emit(DirectoryUpdate.Snapshot(result.state.directory))
                                }
                                DeviceExchangeDirectoryState.Outcome.Gap -> control.post(LIST)
                                DeviceExchangeDirectoryState.Outcome.Stale -> Unit
                            }
                        }
                        DeviceExchangeEnvelope.TYPE_REFUSED -> throw DeviceExchangeRefusedException(
                            event.value.string(DeviceExchangeEnvelope.KEY_REASON) ?: "unknown"
                        )
                    }
                }
            }
        } finally {
            control.removeListener(listener)
            events.cancel()
        }
    }.flowOn(Dispatchers.IO)

    private sealed interface Event {
        data class Open(val welcome: DeviceExchangeWelcome) : Event
        data object Closed : Event
        data class Envelope(val value: DeviceExchangeEnvelope) : Event
    }

    companion object {
        private val LIST = DeviceExchangeEnvelope.of(DeviceExchangeEnvelope.TYPE_LIST)
        private val SUBSCRIBE = DeviceExchangeEnvelope.of(DeviceExchangeEnvelope.TYPE_SUBSCRIBE)
        private val DIRECTORY_TYPES = setOf(DeviceExchangeEnvelope.TYPE_DIRECTORY,
            DeviceExchangeEnvelope.TYPE_CHANGED, DeviceExchangeEnvelope.TYPE_REFUSED)
    }
}
