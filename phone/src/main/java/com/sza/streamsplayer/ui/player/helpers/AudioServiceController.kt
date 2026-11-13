package com.sza.streamsplayer.ui.player.helpers

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.sza.streamsplayer.ui.player.AudioPlaybackService
import timber.log.Timber

/**
 * Manages connection to AudioPlaybackService via MediaController.
 * Provides a Player interface that controls background radio playback.
 */
class AudioServiceController(
    private val context: Context,
) {
    private val controllerLock = Any()
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null

    /** Whether the controller is currently connected to the service */
    val isConnected: Boolean
        get() = synchronized(controllerLock) { mediaController?.isConnected == true }

    /** Returns the MediaController as a Player (or null if not connected) */
    val player: Player?
        get() = synchronized(controllerLock) { mediaController }

    fun connect(onFailed: (() -> Unit)? = null, onConnected: (Player) -> Unit) {
        val connectionRequest = getOrCreateControllerFuture("connect")
        when (connectionRequest) {
            is ControllerConnectionRequest.Connected -> {
                onConnected(connectionRequest.controller)
                return
            }
            is ControllerConnectionRequest.FutureRequest -> Unit
        }

        val future = (connectionRequest as ControllerConnectionRequest.FutureRequest).future
        future.addListener({
            try {
                val controller = future.get()
                when (storeResolvedController(future, controller)) {
                    ControllerStoreResult.Stale -> {
                        controller.release()
                        onFailed?.invoke()
                        return@addListener
                    }
                    ControllerStoreResult.StoredNew,
                    ControllerStoreResult.ReusedExisting -> Unit
                }
                onConnected(controller)
            } catch (e: Exception) {
                clearFutureOnFailure(future)
                Timber.e(e, "AudioServiceController: failed to connect")
                onFailed?.invoke()
            }
        }, MoreExecutors.directExecutor())
    }

    fun connectForStatus(onResult: (player: Player?) -> Unit) {
        val connectionRequest = getOrCreateControllerFuture("connectForStatus")
        when (connectionRequest) {
            is ControllerConnectionRequest.Connected -> {
                onResult(connectionRequest.controller)
                return
            }
            is ControllerConnectionRequest.FutureRequest -> Unit
        }

        val future = (connectionRequest as ControllerConnectionRequest.FutureRequest).future
        future.addListener({
            try {
                val controller = future.get()
                when (storeResolvedController(future, controller)) {
                    ControllerStoreResult.Stale -> {
                        controller.release()
                        onResult(null)
                        return@addListener
                    }
                    ControllerStoreResult.StoredNew,
                    ControllerStoreResult.ReusedExisting -> Unit
                }
                onResult(controller)
            } catch (e: Exception) {
                clearFutureOnFailure(future)
                Timber.w(e, "AudioServiceController: connectForStatus - failed")
                onResult(null)
            }
        }, MoreExecutors.directExecutor())
    }

    fun playAudioWithMetadata(
        uri: Uri,
        title: String,
        artist: String? = null,
        mimeType: String? = null,
        shouldPlay: () -> Boolean = { true },
        onPlayerReady: (Player) -> Unit,
    ) {
        connect { player ->
            if (!shouldPlay()) return@connect
            Timber.d("AudioServiceController: playAudioWithMetadata uri=%s title=%s artist=%s", uri, title, artist)
            val mediaItemBuilder = MediaItem.Builder()
                .setUri(uri)
                .setRequestMetadata(MediaItem.RequestMetadata.Builder().setMediaUri(uri).build())
            if (mimeType != null) {
                mediaItemBuilder.setMimeType(mimeType)
            }
            val metadataBuilder = MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist)
            val mediaItem = mediaItemBuilder
                .setMediaMetadata(metadataBuilder.build())
                .build()
            player.setMediaItem(mediaItem)
            player.repeatMode = Player.REPEAT_MODE_OFF
            player.prepare()
            player.play()
            onPlayerReady(player)
        }
    }

    fun release() {
        val (futureToCancel, controllerToRelease) = synchronized(controllerLock) {
            val future = controllerFuture
            val controller = mediaController
            controllerFuture = null
            mediaController = null
            future to controller
        }
        futureToCancel?.cancel(true)
        controllerToRelease?.release()
    }

    private fun getOrCreateControllerFuture(callerTag: String): ControllerConnectionRequest {
        synchronized(controllerLock) {
            val existingController = mediaController
            if (existingController != null && existingController.isConnected) {
                return ControllerConnectionRequest.Connected(existingController)
            }
            val activeFuture = controllerFuture
            if (activeFuture != null && !activeFuture.isCancelled) {
                return ControllerConnectionRequest.FutureRequest(activeFuture)
            }
            val sessionToken = SessionToken(
                context,
                ComponentName(context, AudioPlaybackService::class.java),
            )
            val future = MediaController.Builder(context, sessionToken).buildAsync()
            controllerFuture = future
            return ControllerConnectionRequest.FutureRequest(future)
        }
    }

    private fun storeResolvedController(
        future: ListenableFuture<MediaController>,
        controller: MediaController,
    ): ControllerStoreResult {
        synchronized(controllerLock) {
            if (controllerFuture !== future) {
                return ControllerStoreResult.Stale
            }
            val existing = mediaController
            return if (existing === controller) {
                ControllerStoreResult.ReusedExisting
            } else {
                mediaController = controller
                ControllerStoreResult.StoredNew
            }
        }
    }

    private fun clearFutureOnFailure(future: ListenableFuture<MediaController>) {
        synchronized(controllerLock) {
            if (controllerFuture === future) {
                controllerFuture = null
            }
        }
    }

    private sealed interface ControllerConnectionRequest {
        data class Connected(val controller: MediaController) : ControllerConnectionRequest
        data class FutureRequest(val future: ListenableFuture<MediaController>) : ControllerConnectionRequest
    }

    private enum class ControllerStoreResult { Stale, StoredNew, ReusedExisting }
}
