package com.sza.streamsplayer.ui.streams.helpers

import com.sza.streamsplayer.core.network.NetworkContextAnalyzer
import com.sza.streamsplayer.data.local.db.StreamSourceEntity
import com.sza.streamsplayer.domain.usecase.streams.GetStreamSourceByUrlUseCase
import javax.inject.Inject

/**
 * S1471: answers the one question a stream shortcut asks before anything is drawn - can this URL play
 * without a screen, or must the main Streams screen open instead.
 */
class StreamShortcutRouteManager @Inject constructor(
    private val getStreamSourceByUrl: GetStreamSourceByUrlUseCase,
    private val networkContextAnalyzer: NetworkContextAnalyzer,
) {

    /**
     * The channel to play with no screen, or null when the caller must fall back to the main screen.
     */
    suspend fun headlessSource(url: String): StreamSourceEntity? {
        val source = getStreamSourceByUrl(url) ?: return null
        val qualifies = StreamHeadlessPlayManager.qualifiesForHeadlessPlay(
            source = source,
            backgroundAudioEnabled = true,
            hasNetwork = networkContextAnalyzer.hasAnyNetwork(),
        )
        return if (qualifies) source else null
    }
}
