package com.sza.streamsplayer.ui.player

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.ImageButton
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import com.sza.streamsplayer.R
import com.sza.streamsplayer.ui.common.help.HelpKeys
import com.sza.streamsplayer.ui.common.help.HelpOpenManager
import com.sza.streamsplayer.ui.common.help.HelpSurfaceIds
import com.sza.streamsplayer.ui.common.help.HelpTooltipManager
import com.sza.streamsplayer.data.local.db.StreamSourceEntity
import com.sza.streamsplayer.databinding.ActivityFullscreenPlayerBinding
import com.sza.streamsplayer.domain.usecase.streams.GetStreamQualityMemoryUseCase
import com.sza.streamsplayer.domain.usecase.streams.ObserveStreamSourcesUseCase
import com.sza.streamsplayer.domain.usecase.streams.RecordStreamPlayOutcomeUseCase
import com.sza.streamsplayer.domain.usecase.streams.RecordStreamQualityMemoryUseCase
import com.sza.streamsplayer.domain.usecase.streams.RemoveStreamSourceUseCase
import com.sza.streamsplayer.domain.usecase.streams.StreamTrackPreferenceUseCase
import com.sza.streamsplayer.ui.player.helpers.PictureInPictureManager
import com.sza.streamsplayer.ui.player.helpers.StreamPlaybackCallback
import com.sza.streamsplayer.ui.player.helpers.StreamPlaybackHelper
import com.sza.streamsplayer.ui.player.helpers.StreamWaitPhase
import com.sza.streamsplayer.ui.player.helpers.TrackInfo
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlin.coroutines.resume
import timber.log.Timber
import javax.inject.Inject

@UnstableApi
@AndroidEntryPoint
class FullscreenPlayerActivity : AppCompatActivity(), StreamPlaybackCallback {

    @Inject
    lateinit var trackPreferenceUseCase: StreamTrackPreferenceUseCase

    @Inject
    lateinit var getQualityMemoryUseCase: GetStreamQualityMemoryUseCase

    @Inject
    lateinit var recordQualityMemoryUseCase: RecordStreamQualityMemoryUseCase

    @Inject
    lateinit var observeStreamSourcesUseCase: ObserveStreamSourcesUseCase

    @Inject
    lateinit var removeStreamSourceUseCase: RemoveStreamSourceUseCase

    @Inject
    lateinit var recordStreamPlayOutcomeUseCase: RecordStreamPlayOutcomeUseCase

    @Inject
    lateinit var descriptorStore: com.sza.streamsplayer.data.broadcast.BroadcastDescriptorStore

    @Inject
    lateinit var playbackResolver: com.sza.streamsplayer.data.exchange.BroadcastPlaybackResolver

    @Inject lateinit var broadcastPasswords: com.sza.streamsplayer.domain.repository.BroadcastPasswordStore
    @Inject lateinit var streamRepository: com.sza.streamsplayer.data.repository.StreamSourceRepository
    private var broadcastPassword: String? = null
    private var passwordUrl: String? = null
    private val broadcastPasswordPrompt by lazy {
        com.sza.streamsplayer.ui.streams.helpers.StreamBroadcastPasswordPromptManager(this)
    }

    private var currentEndpointIndex: Int = 0
    private var currentDescriptor: com.sza.streamsplayer.data.broadcast.BroadcastDescriptorDto? = null
    private var resolutionJob: Job? = null
    private var networkObserver: com.sza.streamsplayer.ui.player.helpers.BroadcastNetworkObserver? = null

    private var _binding: ActivityFullscreenPlayerBinding? = null
    private val binding get() = _binding!!

    private lateinit var playbackHelper: StreamPlaybackHelper
    private var pipManager: PictureInPictureManager? = null

    private var currentChannelId: String = ""
    private var currentUrl: String = ""
    private var currentTitle: String = ""
    private var currentSource: StreamSourceEntity? = null

    private var videoChannels: List<StreamSourceEntity> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        _binding = ActivityFullscreenPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        extractIntentData(intent)

        playbackHelper = StreamPlaybackHelper(
            context = this,
            scope = lifecycleScope,
            callback = this,
            trackPreferenceUseCase = trackPreferenceUseCase,
            getQualityMemoryUseCase = getQualityMemoryUseCase,
            recordQualityMemoryUseCase = recordQualityMemoryUseCase,
        )

        setupControls()
        setupPictureInPicture()
        observeVideoChannels()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                finish()
            }
        })

        startPlayback(currentUrl)
        networkObserver = com.sza.streamsplayer.ui.player.helpers.BroadcastNetworkObserver(this) {
            if (currentDescriptor != null && (resolutionJob?.isActive == true || playbackHelper.exoPlayer?.playWhenReady == true)) {
                Timber.d("S0020: network changed; restarting endpoint order")
                startPlayback(currentUrl)
            }
        }
    }

    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        if (HelpKeys.handle(this, HelpSurfaceIds.PLAYER, event)) return true
        return super.dispatchKeyEvent(event)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        extractIntentData(intent)
        startPlayback(currentUrl)
    }

    private fun extractIntentData(intent: Intent) {
        currentChannelId = intent.getStringExtra(EXTRA_CHANNEL_ID).orEmpty()
        currentUrl = intent.getStringExtra(EXTRA_CHANNEL_URL).orEmpty()
        currentTitle = intent.getStringExtra(EXTRA_CHANNEL_TITLE).orEmpty()
    }

    private fun setupControls() {
        val controls = binding.playerView.findViewById<android.view.View>(R.id.player_controls_root)
        // S0035: the top bar sits below the status bar, so its controls, the help action among them, can be reached by touch.
        controls?.findViewById<android.view.View>(R.id.topBar)?.let { topBar ->
            val basePadding = topBar.paddingTop
            androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(topBar) { view, insets ->
                val bars = insets.getInsets(
                    androidx.core.view.WindowInsetsCompat.Type.statusBars() or androidx.core.view.WindowInsetsCompat.Type.displayCutout(),
                )
                view.setPadding(view.paddingLeft, basePadding + bars.top, view.paddingRight, view.paddingBottom)
                insets
            }
            androidx.core.view.ViewCompat.requestApplyInsets(topBar)
        }
        val tvTitle = controls?.findViewById<TextView>(R.id.tvPlayerTitle)
        tvTitle?.text = currentTitle

        controls?.findViewById<ImageButton>(R.id.btnBack)?.setOnClickListener {
            finish()
        }

        controls?.findViewById<ImageButton>(R.id.btnTracks)?.setOnClickListener {
            showTracksDialog()
        }

        controls?.findViewById<ImageButton>(R.id.btnHelp)?.let { helpButton ->
            helpButton.setOnClickListener { HelpOpenManager.open(this, HelpSurfaceIds.PLAYER) }
            androidx.appcompat.widget.TooltipCompat.setTooltipText(helpButton, HelpTooltipManager.tooltipText(this))
        }

        controls?.findViewById<ImageButton>(R.id.btnPrevChannel)?.setOnClickListener {
            navigateChannel(step = -1)
        }

        controls?.findViewById<ImageButton>(R.id.btnNextChannel)?.setOnClickListener {
            navigateChannel(step = 1)
        }
    }

    private fun setupPictureInPicture() {
        val controls = binding.playerView.findViewById<android.view.View>(R.id.player_controls_root)
        val chromeViews = listOfNotNull(controls, binding.bufferingContainer)

        val manager = PictureInPictureManager(
            activity = this,
            playerView = binding.playerView,
            chromeToHide = chromeViews,
            getPlayer = { playbackHelper.exoPlayer },
            onPlay = { playbackHelper.exoPlayer?.play() },
            onPause = { playbackHelper.exoPlayer?.pause() },
            isVideoPlaying = { playbackHelper.exoPlayer?.isPlaying == true },
        )
        pipManager = manager
        manager.setupPipButton(enablePip = true, isAudio = false)
    }

    private fun observeVideoChannels() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                observeStreamSourcesUseCase().collectLatest { sources ->
                    videoChannels = sources.filter { !it.mediaKind.equals("AUDIO", ignoreCase = true) }
                    currentSource = sources.firstOrNull { it.url == currentUrl || it.id == currentChannelId }
                    updateNavButtonsState()
                }
            }
        }
    }

    private fun updateNavButtonsState() {
        val controls = binding.playerView.findViewById<android.view.View>(R.id.player_controls_root) ?: return
        val prevBtn = controls.findViewById<ImageButton>(R.id.btnPrevChannel)
        val nextBtn = controls.findViewById<ImageButton>(R.id.btnNextChannel)

        val index = videoChannels.indexOfFirst { it.url == currentUrl }
        val hasPrev = index > 0
        val hasNext = index >= 0 && index < videoChannels.size - 1

        prevBtn?.isEnabled = hasPrev
        prevBtn?.alpha = if (hasPrev) 1.0f else 0.4f
        nextBtn?.isEnabled = hasNext
        nextBtn?.alpha = if (hasNext) 1.0f else 0.4f
    }

    private fun navigateChannel(step: Int) {
        val currentIndex = videoChannels.indexOfFirst { it.url == currentUrl }
        val newIndex = currentIndex + step
        if (newIndex in videoChannels.indices) {
            val nextChannel = videoChannels[newIndex]
            currentChannelId = nextChannel.id
            currentUrl = nextChannel.url
            currentTitle = nextChannel.title
            currentSource = nextChannel

            val controls = binding.playerView.findViewById<android.view.View>(R.id.player_controls_root)
            controls?.findViewById<TextView>(R.id.tvPlayerTitle)?.text = currentTitle

            updateNavButtonsState()
            startPlayback(currentUrl)
        }
    }

    private fun startPlayback(url: String, startIndex: Int = 0) {
        if (url.isBlank()) return
        if (passwordUrl != url) { broadcastPassword = null; passwordUrl = url }
        resolutionJob?.cancel()
        playbackHelper.releasePlayer()
        resolutionJob = lifecycleScope.launch {
            val descriptor = descriptorStore.getByUrl(url)
                ?: currentChannelId.takeIf { it.isNotBlank() }?.let { descriptorStore.getBySourceId(it) }
            currentDescriptor = descriptor

            if (descriptor != null) {
                currentEndpointIndex = startIndex
                val resolved = try {
                    playbackResolver.resolveNext(descriptor, startIndex = startIndex, password = broadcastPassword) { presented, hostPort ->
                        promptUserForCertificate(presented, hostPort)
                    }
                } catch (_: com.sza.streamsplayer.domain.player.BroadcastPasswordRequiredException) {
                    askBroadcastPassword(false)
                    return@launch
                } catch (_: com.sza.streamsplayer.data.exchange.UnsupportedBroadcastAuthException) {
                    android.widget.Toast.makeText(this@FullscreenPlayerActivity, R.string.broadcast_auth_unsupported,
                        android.widget.Toast.LENGTH_LONG).show()
                    return@launch
                } catch (e: com.sza.streamsplayer.data.exchange.ExchangeCertificateChangedException) {
                    showCertChangedDialog(e.pinnedFingerprint, e.presentedFingerprint)
                    return@launch
                }

                if (resolved != null) {
                    currentEndpointIndex = resolved.candidateIndex
                    playbackHelper.playStreamVideo(
                        path = resolved.playUrl,
                        playWhenReady = true,
                        targetLatencyMs = resolved.targetLatencyMs,
                        forceUseRtpTcp = resolved.isRtspTcp,
                        closeableProxy = resolved.closeableProxy,
                    )
                    binding.playerView.player = playbackHelper.exoPlayer
                    return@launch
                } else {
                    showStreamNotAvailableDialog()
                    return@launch
                }
            }

            // Normal stream playback fallback
            playbackHelper.playStreamVideo(url, playWhenReady = true)
            binding.playerView.player = playbackHelper.exoPlayer
        }
    }

    private suspend fun askBroadcastPassword(refused: Boolean) {
        val value = broadcastPasswordPrompt.ask(refused) ?: return
        broadcastPassword = value
        val source = streamRepository.getByUrl(currentUrl)
        val user = currentDescriptor?.auth?.user
        if (source != null && user != null && !broadcastPasswords.put(source.id, user, value)) {
            android.widget.Toast.makeText(this, R.string.broadcast_password_save_failed,
                android.widget.Toast.LENGTH_LONG).show()
            return
        }
        startPlayback(currentUrl)
    }

    private suspend fun promptUserForCertificate(presentedFingerprint: String, hostPort: String): Boolean =
        kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
            val dialog = AlertDialog.Builder(this)
                .setIcon(R.drawable.ic_exchange_mark_small)
                .setTitle(R.string.cert_pin_title)
                .setMessage(getString(R.string.cert_pin_message, presentedFingerprint))
                .setPositiveButton(R.string.cert_pin_accept) { _, _ ->
                    if (continuation.isActive) continuation.resume(true)
                }
                .setNegativeButton(R.string.action_cancel) { _, _ ->
                    if (continuation.isActive) continuation.resume(false)
                }
                .setOnCancelListener {
                    if (continuation.isActive) continuation.resume(false)
                }
                .show()
            continuation.invokeOnCancellation { runOnUiThread { dialog.dismiss() } }
        }

    private fun showCertChangedDialog(pinned: String?, presented: String?) {
        AlertDialog.Builder(this)
            .setIcon(R.drawable.ic_exchange_mark_small)
            .setTitle(R.string.cert_changed_title)
            .setMessage(getString(R.string.cert_changed_message, pinned.orEmpty(), presented.orEmpty()))
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun showStreamNotAvailableDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.streams_unavailable_title)
            .setMessage(R.string.stream_not_available)
            .setPositiveButton(android.R.string.ok) { _, _ -> finish() }
            .show()
    }

    private fun showTracksDialog() {
        val audioTracks = playbackHelper.getAvailableAudioTracks()
        val subtitleTracks = playbackHelper.getAvailableSubtitleTracks()

        val items = mutableListOf<String>()
        val actions = mutableListOf<() -> Unit>()

        if (audioTracks.isNotEmpty()) {
            items.add("--- ${getString(R.string.tracks_audio)} ---")
            actions.add { }
            for (track in audioTracks) {
                val selectedPrefix = if (track.isSelected) "✓ " else "   "
                items.add("$selectedPrefix${track.label}")
                actions.add { playbackHelper.selectAudioTrack(track) }
            }
        }

        items.add("--- ${getString(R.string.tracks_subtitles)} ---")
        actions.add { }

        val hasSelectedSubtitle = subtitleTracks.any { it.isSelected }
        val offPrefix = if (!hasSelectedSubtitle) "✓ " else "   "
        items.add("$offPrefix${getString(R.string.tracks_off)}")
        actions.add { playbackHelper.selectSubtitleTrack(null) }

        for (track in subtitleTracks) {
            val selectedPrefix = if (track.isSelected) "✓ " else "   "
            items.add("$selectedPrefix${track.label}")
            actions.add { playbackHelper.selectSubtitleTrack(track) }
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.tracks_title)
            .setItems(items.toTypedArray()) { _, which ->
                actions.getOrNull(which)?.invoke()
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    // --- StreamPlaybackCallback ---

    override fun onBuffering(isBuffering: Boolean) {
        binding.bufferingContainer.isVisible = isBuffering
    }

    override fun onStreamWaitPhase(phase: StreamWaitPhase?) {
        if (phase == null) {
            binding.bufferingContainer.isVisible = false
        } else {
            binding.bufferingContainer.isVisible = true
            binding.tvWaitPhase.setText(
                if (phase == StreamWaitPhase.RECONNECTING) R.string.stream_wait_reconnecting
                else R.string.stream_wait_buffering,
            )
        }
    }

    override fun onPlaybackReady() {
        binding.bufferingContainer.isVisible = false
        if (currentChannelId.isNotBlank()) {
            lifecycleScope.launch {
                recordStreamPlayOutcomeUseCase.recordPlaySuccess(currentChannelId)
            }
        }
    }

    override fun onPlaybackStateChanged(isPlaying: Boolean) {
        pipManager?.updatePipActions()
    }

    override fun onPlaybackEnded() {
        Timber.i("FullscreenPlayerActivity: stream ended")
    }

    override fun onPlaybackError(error: PlaybackException) {
        binding.bufferingContainer.isVisible = false
        if (isFinishing || isDestroyed) return

        if (com.sza.streamsplayer.domain.player.BroadcastPlaybackPairs.isPasswordRefusal(error)) {
            playbackHelper.releasePlayer()
            lifecycleScope.launch { askBroadcastPassword(true) }
            return
        }

        // If this is a broadcast with multiple endpoints, try the next one
        val descriptor = currentDescriptor
        if (descriptor != null) {
            val supported = playbackResolver.filterSupportedEndpoints(descriptor.getEffectiveEndpoints())
            if (currentEndpointIndex + 1 < supported.size) {
                Timber.d("S0020: playback error on candidate $currentEndpointIndex, trying next candidate ${currentEndpointIndex + 1}")
                startPlayback(currentUrl, startIndex = currentEndpointIndex + 1)
                return
            }
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.streams_unavailable_title)
            .setMessage(getString(R.string.streams_unavailable_message, currentTitle))
            .setPositiveButton(R.string.action_retry) { _, _ ->
                startPlayback(currentUrl, startIndex = 0)
            }
            .setNegativeButton(R.string.action_delete) { _, _ ->
                currentSource?.let { source ->
                    lifecycleScope.launch {
                        removeStreamSourceUseCase(source)
                        finish()
                    }
                } ?: finish()
            }
            .setNeutralButton(R.string.action_cancel) { _, _ ->
                finish()
            }
            .setCancelable(false)
            .show()
    }

    override fun onStreamProgramName(name: String) {
        val controls = binding.playerView.findViewById<android.view.View>(R.id.player_controls_root)
        controls?.findViewById<TextView>(R.id.tvPlayerTitle)?.text = "$currentTitle - $name"
    }

    // --- Lifecycle & PiP ---

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        pipManager?.onUserLeaveHint(enablePip = true)
    }

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode)
        pipManager?.onPictureInPictureModeChanged(isInPictureInPictureMode)
    }

    override fun onStop() {
        super.onStop()
        val isInPip = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && isInPictureInPictureMode
        if (!isInPip) {
            resolutionJob?.cancel()
            playbackHelper.exoPlayer?.pause()
        }
    }

    override fun onDestroy() {
        broadcastPasswordPrompt.close()
        broadcastPassword = null
        resolutionJob?.cancel()
        networkObserver?.close()
        networkObserver = null
        super.onDestroy()
        pipManager?.release()
        pipManager = null
        playbackHelper.releasePlayer()
        _binding = null
    }

    companion object {
        const val EXTRA_CHANNEL_ID = "extra_channel_id"
        const val EXTRA_CHANNEL_URL = "extra_channel_url"
        const val EXTRA_CHANNEL_TITLE = "extra_channel_title"

        fun createIntent(context: Context, channel: StreamSourceEntity): Intent =
            Intent(context, FullscreenPlayerActivity::class.java).apply {
                putExtra(EXTRA_CHANNEL_ID, channel.id)
                putExtra(EXTRA_CHANNEL_URL, channel.url)
                putExtra(EXTRA_CHANNEL_TITLE, channel.title)
            }
    }
}
