package com.sza.streamsplayer.wear.ui.streams.own

import com.sza.streamsplayer.data.broadcast.BroadcastDescriptorDto
import com.sza.streamsplayer.data.broadcast.BroadcastDescriptorParser
import com.sza.streamsplayer.data.exchange.ExchangeAccountEntry
import com.sza.streamsplayer.data.exchange.ExchangeAccountState
import com.sza.streamsplayer.data.exchange.directory.AccountBroadcastDirectory
import com.sza.streamsplayer.wear.data.exchange.WearBroadcastDescriptorStore
import com.sza.streamsplayer.wear.data.exchange.WearExchangeEntryStore
import com.sza.streamsplayer.wear.data.exchange.WearExchangePresence
import com.sza.streamsplayer.wear.domain.model.WearStreamChannel
import com.sza.streamsplayer.wear.domain.model.WearStreamPlaybackTarget
import com.sza.streamsplayer.wear.domain.model.WearTileKind
import com.sza.streamsplayer.wear.domain.repository.WearStreamChannelRepository
import com.sza.streamsplayer.wear.domain.usecase.PrepareEphemeralStreamPlaybackUseCase
import com.sza.streamsplayer.wear.domain.usecase.RequestWearTileRefreshUseCase
import com.sza.streamsplayer.wear.util.rethrowIfCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject

/** S0038: what the watch's own exchange account allows the Own list to do. */
enum class WearOwnAccount { NONE, CONNECTED, SIGN_IN_REQUIRED }

/**
 * S0038: everything the watch's Own category needs from the exchange - the shared account directory, the
 * connection hold, and keeping or playing a live broadcast - so the streams list takes one collaborator.
 */
class WearOwnStreamsSource @Inject constructor(
    val directory: AccountBroadcastDirectory,
    private val presence: WearExchangePresence,
    entryStore: WearExchangeEntryStore,
    private val parser: BroadcastDescriptorParser,
    private val descriptors: WearBroadcastDescriptorStore,
    private val repository: WearStreamChannelRepository,
    private val playEphemeral: PrepareEphemeralStreamPlaybackUseCase,
    private val requestTileRefresh: RequestWearTileRefreshUseCase,
) {
    val account: Flow<WearOwnAccount> = entryStore.entries.map(::accountOf)

    /** The Own list is in front (true) or not; only while it is does the control stream stay up for it. */
    fun hold(open: Boolean) = presence.listing(open)

    fun parse(descriptorJson: String): BroadcastDescriptorDto? = parser.parse(descriptorJson)

    sealed interface KeepResult {
        data class Kept(val channel: WearStreamChannel) : KeepResult
        data object Unsupported : KeepResult
        data object Failed : KeepResult
    }

    /**
     * Keeps a live broadcast as a personal channel, or moves the channel already kept for it to the current
     * address. The kept channel's id and name stay; keeping again writes the same row, never a second one.
     */
    @Suppress("TooGenericExceptionCaught")
    suspend fun keep(row: WearLiveBroadcast, descriptorJson: String): KeepResult {
        val dto = parser.parse(descriptorJson)
        if (dto == null || WearOwnStreamsProjector.limitOf(dto.mode, dto) != null) return KeepResult.Unsupported
        return try {
            if (!descriptors.save(dto.url, dto)) return KeepResult.Failed
            val all = repository.getAllChannels()
            val previous = row.saved?.let { saved -> all.firstOrNull { it.id == saved.id } }
                ?: all.firstOrNull { it.url == dto.url.trim() && it.origin == WearStreamChannel.ORIGIN_PHONE }
            val channel = WearStreamChannel(
                id = previous?.id ?: UUID.randomUUID().toString(),
                name = previous?.name ?: row.title ?: dto.title?.takeIf { it.isNotBlank() } ?: dto.url,
                url = dto.url.trim(),
                mediaKind = AUDIO,
                origin = WearStreamChannel.ORIGIN_PHONE,
                broadcastSourceId = dto.sourceId,
                exchangeEntryId = row.entryId,
                exchangeBroadcastId = row.broadcastId,
            )
            // In place when a channel is kept already, so its position and pins hold; one row per address either way.
            val next = if (previous == null) {
                all.filterNot { it.url == channel.url } + channel
            } else {
                all.mapNotNull { existing ->
                    when {
                        existing.id == channel.id -> channel
                        existing.url == channel.url -> null
                        else -> existing
                    }
                }
            }
            repository.saveChannels(next)
            requestTileRefresh(WearTileKind.STREAM)
            KeepResult.Kept(channel)
        } catch (e: Exception) {
            e.rethrowIfCancellation()
            Timber.w(e, "Failed to keep a live broadcast")
            KeepResult.Failed
        }
    }

    /**
     * Opens a live broadcast that is not kept as a channel. The descriptor is stored under its address so the
     * audio player resolves its endpoints in the S0020 order; nothing enters the channel list, history or recents.
     */
    @Suppress("TooGenericExceptionCaught")
    suspend fun playUnsaved(dto: BroadcastDescriptorDto, title: String): WearStreamPlaybackTarget? = try {
        if (descriptors.save(dto.url, dto)) playEphemeral(dto.url.trim(), title, isVideo = false) else null
    } catch (e: Exception) {
        e.rethrowIfCancellation()
        Timber.w(e, "Failed to open a live broadcast")
        null
    }

    companion object {
        private const val AUDIO = "AUDIO"

        fun accountOf(entry: ExchangeAccountEntry?): WearOwnAccount = when (entry?.state) {
            null -> WearOwnAccount.NONE
            ExchangeAccountState.Connected -> WearOwnAccount.CONNECTED
            else -> WearOwnAccount.SIGN_IN_REQUIRED
        }
    }
}
