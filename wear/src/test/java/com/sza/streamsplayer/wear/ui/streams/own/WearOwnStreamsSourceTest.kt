package com.sza.streamsplayer.wear.ui.streams.own

import com.sza.streamsplayer.data.broadcast.BroadcastDescriptorParser
import com.sza.streamsplayer.data.exchange.ExchangeAccountEntry
import com.sza.streamsplayer.data.exchange.ExchangeAccountState
import com.sza.streamsplayer.data.exchange.directory.AccountBroadcastDirectory
import com.sza.streamsplayer.wear.data.exchange.WearBroadcastDescriptorStore
import com.sza.streamsplayer.wear.data.exchange.WearExchangeEntryStore
import com.sza.streamsplayer.wear.data.exchange.WearExchangePresence
import com.sza.streamsplayer.wear.domain.model.WearStreamChannel
import com.sza.streamsplayer.wear.domain.repository.WearStreamChannelRepository
import com.sza.streamsplayer.wear.domain.usecase.PrepareEphemeralStreamPlaybackUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** S0038: keeping a live broadcast on the watch, and the account states the Own list tells apart. */
class WearOwnStreamsSourceTest {

    private class FakeRepository(initial: List<WearStreamChannel>) : WearStreamChannelRepository {
        val channels = initial.toMutableList()
        override suspend fun getAllChannels(): List<WearStreamChannel> = channels.toList()
        override fun observeChannels(): Flow<List<WearStreamChannel>> = MutableStateFlow(channels.toList())
        override suspend fun saveChannels(channels: List<WearStreamChannel>) {
            this.channels.clear()
            this.channels.addAll(channels)
        }
        override suspend fun clear() = channels.clear()
        override suspend fun upsertChannel(channel: WearStreamChannel): Boolean = error("not used by keep")
    }

    private val descriptors = mockk<WearBroadcastDescriptorStore>().also {
        coEvery { it.save(any(), any()) } returns true
    }

    private fun source(repository: WearStreamChannelRepository): WearOwnStreamsSource {
        val entryStore = mockk<WearExchangeEntryStore>()
        every { entryStore.entries } returns emptyFlow()
        return WearOwnStreamsSource(
            directory = mockk<AccountBroadcastDirectory>(relaxed = true),
            presence = WearExchangePresence(),
            entryStore = entryStore,
            parser = BroadcastDescriptorParser(),
            descriptors = descriptors,
            repository = repository,
            playEphemeral = mockk<PrepareEphemeralStreamPlaybackUseCase>(relaxed = true),
            requestTileRefresh = mockk(relaxed = true),
        )
    }

    private fun descriptor(url: String, mode: String = "AUDIO_ONLY") =
        "{\"schemaVersion\":1,\"url\":\"$url\",\"mode\":\"$mode\",\"sourceId\":\"mic\"}"

    private fun row(saved: WearStreamChannel? = null, broadcastId: String = "b1") = WearLiveBroadcast(
        entryId = "entry-1", broadcastId = broadcastId, deviceId = "phone", deviceName = "Phone", title = "Kitchen radio",
        kind = WearLiveKind.AUDIO, presence = WearLivePresence.ONLINE, limit = null, saved = saved,
        descriptorJson = descriptor("http://phone/old"),
    )

    private val catalog = WearStreamChannel(id = "c", name = "News", url = "http://catalog/news", mediaKind = "AUDIO")

    @Test
    fun `keeping binds the channel to the account, the broadcast and its source, and keeping again adds nothing`() = runBlocking {
        val repository = FakeRepository(listOf(catalog))
        val source = source(repository)

        val first = source.keep(row(), descriptor("http://phone/old"))
        val kept = (first as WearOwnStreamsSource.KeepResult.Kept).channel
        assertEquals("Kitchen radio", kept.name)
        assertEquals(WearStreamChannel.ORIGIN_PHONE, kept.origin)
        assertEquals("mic", kept.broadcastSourceId)
        assertEquals("entry-1", kept.exchangeEntryId)
        assertEquals("b1", kept.exchangeBroadcastId)

        source.keep(row(), descriptor("http://phone/old"))
        assertEquals(listOf("c", kept.id), repository.channels.map { it.id })
        coVerify { descriptors.save("http://phone/old", any()) }
    }

    @Test
    fun `a kept channel follows its source to a new address in place, keeping its id and name`() = runBlocking {
        val existing = WearStreamChannel(
            id = "k", name = "My radio", url = "http://phone/old", mediaKind = "AUDIO",
            origin = WearStreamChannel.ORIGIN_PHONE, broadcastSourceId = "mic",
        )
        val repository = FakeRepository(listOf(existing, catalog))
        val source = source(repository)

        source.keep(row(saved = existing, broadcastId = "b2"), descriptor("http://phone/new"))
        assertEquals(listOf("k", "c"), repository.channels.map { it.id })
        val moved = repository.channels.first()
        assertEquals("http://phone/new", moved.url)
        assertEquals("My radio", moved.name)
        assertEquals("b2", moved.exchangeBroadcastId)
    }

    @Test
    fun `a broadcast the watch cannot play is not kept`() = runBlocking {
        val repository = FakeRepository(emptyList())
        val result = source(repository).keep(row(), descriptor("http://phone/v", mode = "VIDEO_AUDIO"))
        assertEquals(WearOwnStreamsSource.KeepResult.Unsupported, result)
        assertTrue(repository.channels.isEmpty())
    }

    @Test
    fun `holding the list holds the connection only`() {
        val presence = WearExchangePresence()
        presence.resumed(true)
        presence.interactive(true)
        val entryStore = mockk<WearExchangeEntryStore>()
        every { entryStore.entries } returns emptyFlow()
        val source = WearOwnStreamsSource(
            mockk(relaxed = true), presence, entryStore, BroadcastDescriptorParser(), descriptors,
            FakeRepository(emptyList()), mockk(relaxed = true), mockk(relaxed = true),
        )
        source.hold(true)
        assertTrue(presence.connection.value)
        source.hold(false)
        assertEquals(false, presence.connection.value)
    }

    @Test
    fun `the account states are none, connected and sign-in required`() {
        fun entry(state: ExchangeAccountState) =
            ExchangeAccountEntry(entryId = "e", endpoint = "x:443", login = "o", deviceId = "w", state = state)
        assertEquals(WearOwnAccount.NONE, WearOwnStreamsSource.accountOf(null))
        assertEquals(WearOwnAccount.CONNECTED, WearOwnStreamsSource.accountOf(entry(ExchangeAccountState.Connected)))
        for (state in listOf(ExchangeAccountState.NeedsSignIn, ExchangeAccountState.CertificateChanged, ExchangeAccountState.Revoked)) {
            assertEquals(WearOwnAccount.SIGN_IN_REQUIRED, WearOwnStreamsSource.accountOf(entry(state)))
        }
    }
}
