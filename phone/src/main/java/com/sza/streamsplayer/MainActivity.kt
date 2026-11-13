package com.sza.streamsplayer

import android.os.Bundle
import android.content.Intent
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.sza.streamsplayer.core.streams.FaviconAtlasSlicer
import com.sza.streamsplayer.data.local.db.StreamSourceEntity
import com.sza.streamsplayer.data.repository.streams.ChannelPreviewAtlasStore
import com.sza.streamsplayer.data.repository.streams.FaviconAtlasStore
import com.sza.streamsplayer.data.repository.streams.StreamLogoAtlasStore
import com.sza.streamsplayer.domain.delivery.ArtworkManifestSource
import com.sza.streamsplayer.domain.delivery.DeliverableInventory
import com.sza.streamsplayer.domain.delivery.DeliverableSet
import com.sza.streamsplayer.databinding.ActivityMainBinding
import com.sza.streamsplayer.distribution.Distribution
import com.sza.streamsplayer.domain.model.DisplayMode
import com.sza.streamsplayer.ui.common.help.HelpKeys
import com.sza.streamsplayer.ui.common.help.HelpOpenManager
import com.sza.streamsplayer.ui.common.help.HelpSurfaceIds
import com.sza.streamsplayer.ui.common.help.HelpTooltipManager
import com.sza.streamsplayer.ui.player.FullscreenPlayerActivity
import com.sza.streamsplayer.ui.player.helpers.AudioServiceController
import com.sza.streamsplayer.ui.scanner.ScanQrContract
import com.sza.streamsplayer.ui.scanner.ScanResult
import com.sza.streamsplayer.ui.streams.StreamChannelActionsSheet
import com.sza.streamsplayer.ui.streams.ChannelPreviewAtlasSlicer
import com.sza.streamsplayer.ui.streams.StreamChannelAdapter
import com.sza.streamsplayer.ui.streams.StreamLogoAtlasSlicer
import com.sza.streamsplayer.ui.streams.StreamTilePackReader
import com.sza.streamsplayer.ui.streams.helpers.StreamAtlasPromptManager
import com.sza.streamsplayer.ui.streams.artworkIndexIsOutOfStep
import com.sza.streamsplayer.ui.streams.usesPreviewTier
import com.sza.streamsplayer.ui.streams.StreamEditDialog
import com.sza.streamsplayer.ui.streams.StreamFilterBottomSheet
import com.sza.streamsplayer.ui.streams.helpers.StreamInlineAudioCallbacks
import com.sza.streamsplayer.ui.streams.helpers.StreamInlineAudioManager
import com.sza.streamsplayer.ui.streams.helpers.StreamInlineAudioViews
import com.sza.streamsplayer.ui.streams.StreamsViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@UnstableApi
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject
    lateinit var distribution: Distribution

    @Inject
    lateinit var faviconStore: FaviconAtlasStore

    // S0050: the on-demand artwork payloads and the offer path that fills them.
    @Inject lateinit var channelPreviewStore: ChannelPreviewAtlasStore
    @Inject lateinit var streamLogoStore: StreamLogoAtlasStore
    @Inject lateinit var deliverableInventory: DeliverableInventory
    @Inject lateinit var artworkManifest: ArtworkManifestSource

    @Inject lateinit var broadcastLinkOpener: com.sza.streamsplayer.ui.streams.helpers.BroadcastLinkOpenManager
    @Inject lateinit var streamRepository: com.sza.streamsplayer.data.repository.StreamSourceRepository
    @Inject lateinit var mediaKindClassifier: com.sza.streamsplayer.domain.usecase.streams.StreamMediaKindClassifier
    @Inject lateinit var sleepTimerManager: com.sza.streamsplayer.core.playback.SleepTimerManager

    private val viewModel: StreamsViewModel by viewModels()
    private val broadcastPasswordPrompt by lazy {
        com.sza.streamsplayer.ui.streams.helpers.StreamBroadcastPasswordPromptManager(this)
    }

    private fun importBroadcast(raw: String) {
        lifecycleScope.launch {
            val password = if (viewModel.needsBroadcastPassword(raw)) broadcastPasswordPrompt.ask() ?: return@launch else null
            viewModel.onImportBroadcast(raw, password)
        }
    }

    private val importBroadcastFileLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            readAndImportBroadcastUri(uri)
        }
    }

    private val importBroadcastQrLauncher = registerForActivityResult(ScanQrContract()) { result ->
        when (result) {
            is ScanResult.Text -> importBroadcast(result.text)
            ScanResult.Denied -> Toast.makeText(this, R.string.qr_scanner_permission_denied_message, Toast.LENGTH_LONG).show()
            ScanResult.NoCamera -> Toast.makeText(this, R.string.qr_scanner_no_camera_message, Toast.LENGTH_LONG).show()
            ScanResult.Cancelled -> Unit
        }
    }

    private var _binding: ActivityMainBinding? = null
    private val binding get() = _binding!!

    private lateinit var catalogInput: com.sza.streamsplayer.ui.streams.helpers.StreamCatalogInputManager
    private lateinit var channelAdapter: StreamChannelAdapter
    private lateinit var inlineAudioManager: StreamInlineAudioManager
    private lateinit var audioController: AudioServiceController
    private lateinit var atlasSlicer: FaviconAtlasSlicer
    private var faviconCoords: Map<String, Int> = emptyMap()

    // S0050: per-tile readers of the two payloads; lazy so they are built after Hilt injection.
    private val previewSlicer by lazy {
        ChannelPreviewAtlasSlicer(
            { channelPreviewStore.atlasFile() },
            StreamTilePackReader({ channelPreviewStore.tilePackFile() }, StreamTilePackReader.budgetBytes(this)),
        )
    }
    private val logoSlicer by lazy {
        StreamLogoAtlasSlicer(
            { streamLogoStore.atlasFile() },
            StreamTilePackReader({ streamLogoStore.tilePackFile() }, StreamTilePackReader.budgetBytes(this)),
        )
    }
    private var previewCoords: Map<String, Int> = emptyMap()
    private var logoCoords: Map<String, Int> = emptyMap()

    // S1154/S1201: two separate payloads, so two separate offers - a user who only wants video previews
    // should not be made to download logos to get them.
    private val previewPromptManager by lazy {
        StreamAtlasPromptManager(
            deliverableInventory,
            lifecycleScope,
            DeliverableSet.CHANNEL_PREVIEW_ATLAS,
            R.string.streams_atlas_prompt_message,
            R.string.streams_atlas_prompt_title,
            artworkManifest,
            ::reloadPreviewTiles,
        )
    }
    private val logoPromptManager by lazy {
        StreamAtlasPromptManager(
            deliverableInventory,
            lifecycleScope,
            DeliverableSet.STREAM_LOGO_ATLAS,
            R.string.streams_logo_prompt_message,
            R.string.streams_logo_prompt_title,
            artworkManifest,
            ::reloadLogoTiles,
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        _binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        Timber.d("S0004: phone catalog shell opened")

        audioController = AudioServiceController(this)
        atlasSlicer = FaviconAtlasSlicer { faviconStore.atlasFile() }

        inlineAudioManager = StreamInlineAudioManager(
            lifecycleOwner = this,
            views = StreamInlineAudioViews(
                miniControl = binding.bottomRadioBar,
                titleView = binding.tvRadioTitle,
                playStopButton = binding.btnRadioPlayPause,
                iconView = binding.ivRadioFavicon,
                closeButton = binding.btnRadioClose,
                sleepTimerButton = binding.btnSleepTimer,
            ),
            audioController = audioController,
            callbacks = StreamInlineAudioCallbacks(
                onPlayingChanged = { id ->
                    channelAdapter.activePlayingId = id
                },
                onPlaybackStateChanged = { _, isPlaying ->
                    channelAdapter.isActivelyPlaying = isPlaying
                },
                onSuccess = { source ->
                    viewModel.recordStreamPlaySuccess(source.id)
                },
                onError = { source ->
                    viewModel.recordStreamPlayFailure(source.id, hasNetwork = true)
                    Toast.makeText(this, R.string.streams_no_signal, Toast.LENGTH_SHORT).show()
                },
                onManualFullStop = {
                    sleepTimerManager.cancel()
                },
            ),
        )

        setupToolbar()
        setupSearch()
        setupMediaChips()
        setupSleepTimer()
        setupRecyclerView()
        catalogInput = com.sza.streamsplayer.ui.streams.helpers.StreamCatalogInputManager(
            binding.root, binding.etSearch,
            onContext = { focus ->
                channelAdapter.showFocusedChannelActions(binding.channelsRecyclerView, focus) ||
                    binding.topToolbar.showOverflowMenu()
            },
        )
        supportFragmentManager.setFragmentResultListener(StreamChannelActionsSheet.RESULT, this) { _, result ->
            val channel = com.google.gson.Gson().fromJson(
                result.getString(StreamChannelActionsSheet.CHANNEL), StreamSourceEntity::class.java,
            )
            when (result.getString(StreamChannelActionsSheet.ACTION)) {
                StreamChannelActionsSheet.PLAY -> handleChannelClick(channel)
                StreamChannelActionsSheet.PIN -> viewModel.onPin(channel)
                StreamChannelActionsSheet.DELETE -> viewModel.onRemove(channel)
                StreamChannelActionsSheet.EDIT -> StreamEditDialog.show(this, channel) { url, title, mediaKind, category ->
                    viewModel.onUpdateStream(channel, url, title, mediaKind, category)
                }
            }
        }
        observeViewModel()
        // S0068: the catalog refresh policy runs once per view model; a rotation does not ask again.
        viewModel.onScreenOpened()
        loadFaviconCoords()
        handleIncomingIntent(intent)
        restoreBackgroundRadio()
    }

    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        if (HelpKeys.handle(this, HelpSurfaceIds.CATALOG, event)) return true
        if (::catalogInput.isInitialized && catalogInput.handleKeyEvent(event, currentFocus)) return true
        return super.dispatchKeyEvent(event)
    }

    override fun dispatchGenericMotionEvent(event: android.view.MotionEvent): Boolean {
        if (::catalogInput.isInitialized && catalogInput.handleMotionEvent(event, currentFocus)) return true
        return super.dispatchGenericMotionEvent(event)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent) {
        if (intent.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            val text = intent.getStringExtra(Intent.EXTRA_TEXT)?.trim()
            if (!text.isNullOrBlank()) {
                intent.action = null
                broadcastLinkOpener.open(this, text)
                return
            }
        }

        val data = intent.data
        if (intent.action == Intent.ACTION_VIEW && data != null) {
            val dataStr = data.toString()
            if (dataStr.startsWith("fmsbcast://import", ignoreCase = true)) {
                intent.action = null
                broadcastLinkOpener.open(this, dataStr)
                return
            }
            val mimeType = intent.type ?: contentResolver.getType(data)
            val isBcastFile = (data.path?.endsWith(".fmsbcast", ignoreCase = true) == true) ||
                mimeType == "application/vnd.fms.bcast+json"
            if (isBcastFile) {
                intent.action = null
                readAndImportBroadcastUri(data, keepAsChannel = false)
                return
            }
        }

        openWearChannel(intent)
    }

    private fun openWearChannel(intent: Intent) {
        if (intent.action != Intent.ACTION_VIEW) return
        val url = intent.data?.toString() ?: return
        if (!mediaKindClassifier.isSupportedScheme(url) || intent.data?.host.isNullOrBlank()) return
        intent.action = null
        lifecycleScope.launch {
            val channel = streamRepository.getByUrl(url) ?: StreamSourceEntity(
                id = java.util.UUID.randomUUID().toString(), url = url,
                title = intent.getStringExtra("stream_name")?.takeIf { it.isNotBlank() } ?: url,
                mediaKind = mediaKindClassifier.resolve(intent.getStringExtra("stream_kind").orEmpty(), url), sourceOrigin = "MANUAL",
                sortIndex = 0, addedAt = System.currentTimeMillis())
            handleChannelClick(channel)
        }
    }

    private fun setupToolbar() {
        HelpTooltipManager.apply(this, binding.topToolbar.menu.findItem(R.id.menu_help))
        binding.topToolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.menu_mode_toggle -> {
                    viewModel.onToggleDisplayMode()
                    true
                }
                R.id.menu_filter -> {
                    showFilterBottomSheet()
                    true
                }
                R.id.menu_add_channel -> {
                    StreamEditDialog.show(this) { url, title, mediaKind, category ->
                        viewModel.onAddManualStream(url, title, mediaKind, category)
                    }
                    true
                }
                R.id.menu_recently_played -> {
                    com.sza.streamsplayer.ui.streams.RecentlyPlayedBottomSheet.newInstance()
                        .show(supportFragmentManager, com.sza.streamsplayer.ui.streams.RecentlyPlayedBottomSheet.TAG)
                    true
                }
                R.id.menu_broadcast -> {
                    com.sza.streamsplayer.ui.broadcast.BroadcastControlActivity.launch(this)
                    true
                }
                R.id.menu_help -> {
                    HelpOpenManager.open(this, HelpSurfaceIds.CATALOG)
                    true
                }
                R.id.menu_import_playlist -> {
                    showImportPlaylistDialog()
                    true
                }
                R.id.menu_import_broadcast -> {
                    showImportBroadcastDialog()
                    true
                }
                R.id.menu_refresh_catalog -> {
                    viewModel.onRefresh()
                    true
                }
                R.id.menu_media_exchange_portal -> {
                    com.sza.streamsplayer.ui.common.support.MediaExchangePortalDialog()
                        .show(supportFragmentManager, "media_exchange_portal")
                    true
                }
                R.id.menu_exchange_server -> {
                    startActivity(Intent(this, com.sza.streamsplayer.ui.exchange.ExchangeActivity::class.java))
                    true
                }
                R.id.menu_settings -> {
                    com.sza.streamsplayer.ui.settings.SettingsActivity.launch(this)
                    true
                }
                else -> false
            }
        }

        binding.btnBannerClearFilters.setOnClickListener {
            viewModel.onClearFilters()
            binding.etSearch.text?.clear()
        }

        binding.btnOwnRefresh.setOnClickListener { viewModel.onRefreshOwn() }
        binding.btnOwnStatusAction.setOnClickListener {
            when (ownAction) {
                OwnAction.SETUP, OwnAction.SIGN_IN ->
                    startActivity(Intent(this, com.sza.streamsplayer.ui.exchange.ExchangeActivity::class.java))
                OwnAction.RETRY -> viewModel.onRefreshOwn()
                null -> Unit
            }
        }
    }

    private enum class OwnAction { SETUP, SIGN_IN, RETRY }

    private var ownAction: OwnAction? = null

    /** S0038: the state of the account directory behind the Own category, in words, with the action that fits it. */
    private fun renderOwnStatus(own: StreamsViewModel.OwnUiState?) {
        binding.ownStatusBanner.isVisible = own != null
        if (own == null) return
        val updated = own.lastUpdatedAtMs?.let { android.text.format.DateFormat.getTimeFormat(this).format(java.util.Date(it)) }
        val staleNote = updated?.let { getString(R.string.own_status_stale, it) } ?: getString(R.string.own_status_stale_unknown)
        var action: OwnAction? = null
        // S0039: EXCHANGE-MARK rule 8 (c) - no server, sign in again and a failure are statuses about the server.
        val aboutServer = own.account == StreamsViewModel.OwnAccount.NONE ||
            own.account == StreamsViewModel.OwnAccount.SIGN_IN_REQUIRED ||
            (own.account != StreamsViewModel.OwnAccount.UNKNOWN && own.failure != null)
        val text = when {
            own.account == StreamsViewModel.OwnAccount.NONE -> {
                action = OwnAction.SETUP
                getString(R.string.own_status_no_server)
            }
            own.account == StreamsViewModel.OwnAccount.SIGN_IN_REQUIRED -> {
                action = OwnAction.SIGN_IN
                getString(R.string.own_status_sign_in)
            }
            own.account == StreamsViewModel.OwnAccount.UNKNOWN -> getString(R.string.own_status_loading)
            own.failure != null -> {
                action = OwnAction.RETRY
                val reason = getString(
                    if (own.failure == StreamsViewModel.OwnFailure.REFUSED) R.string.own_status_refused else R.string.own_status_unreachable,
                )
                if (own.loaded) "$reason\n$staleNote" else reason
            }
            own.loading || (own.refreshing && !own.loaded) -> getString(R.string.own_status_loading)
            own.stale -> staleNote
            updated != null -> getString(R.string.own_status_updated, updated)
            else -> ""
        }
        ownAction = action
        binding.tvOwnStatus.compoundDrawablePadding = resources.getDimensionPixelSize(R.dimen.margin_normal)
        binding.tvOwnStatus.setCompoundDrawablesRelativeWithIntrinsicBounds(
            if (aboutServer) R.drawable.ic_exchange_mark_small else 0, 0, 0, 0,
        )
        binding.tvOwnStatus.text = text
        binding.tvOwnStatus.isVisible = text.isNotEmpty()
        binding.btnOwnStatusAction.isVisible = action != null
        action?.let {
            binding.btnOwnStatusAction.setText(
                when (it) {
                    OwnAction.SETUP -> R.string.own_action_setup
                    OwnAction.SIGN_IN -> R.string.own_action_sign_in
                    OwnAction.RETRY -> R.string.action_retry
                },
            )
        }
        binding.pbOwnStatus.isVisible = own.loading || own.refreshing
    }

    private var catalogBanner: View? = null

    /**
     * S0068 (FastMediaSorter S1823, S4459): the offer of a newly published channel list. A banner, not a
     * Snackbar, so a timeout never decides whether a fresh install gets a channel list; it stays until it is
     * taken or closed. Inflated on its first showing only. Update runs the menu's catalog import; Close
     * remembers the list it was shown for, so only a list published later brings it back.
     */
    private fun renderCatalogOffer(visible: Boolean) {
        if (!visible && catalogBanner == null) return
        val banner = catalogBanner ?: binding.stubCatalogBanner.inflate().also { inflated ->
            catalogBanner = inflated
            inflated.findViewById<View>(R.id.btnCatalogBannerAction).setOnClickListener {
                viewModel.onCatalogOfferAccepted()
            }
            inflated.findViewById<View>(R.id.btnCatalogBannerDismiss).setOnClickListener {
                viewModel.onCatalogOfferDismissed()
            }
        }
        // Focus on a button that is about to hide would be lost; hand it to the search field first.
        if (!visible && banner.hasFocus()) binding.etSearch.requestFocus()
        banner.isVisible = visible
    }

    private fun saveLive(row: com.sza.streamsplayer.ui.streams.own.LiveBroadcast) {
        lifecycleScope.launch {
            val password = if (viewModel.liveNeedsPassword(row)) broadcastPasswordPrompt.ask() ?: return@launch else null
            viewModel.onSaveLive(row, password)
        }
    }

    override fun onStart() {
        super.onStart()
        viewModel.onScreenVisible(true)
        // S1154: a download may have landed while this screen sat in the background - pick it up on
        // return instead of waiting for the next catalog import. S0064: so may a removal from Settings.
        // isInstalled() stats on IO (S3230).
        lifecycleScope.launch {
            if (artworkIndexIsOutOfStep(previewCoords.isNotEmpty(), channelPreviewStore.isInstalled())) {
                Timber.d("S0064: catalog re-reads the channel previews on return")
                reloadPreviewTiles()
            }
            if (artworkIndexIsOutOfStep(logoCoords.isNotEmpty(), streamLogoStore.isInstalled())) {
                Timber.d("S0064: catalog re-reads the station logos on return")
                reloadLogoTiles()
            }
        }
    }

    override fun onStop() {
        viewModel.onScreenVisible(false)
        super.onStop()
    }

    private fun setupSearch() {
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                viewModel.onQueryChanged(s?.toString().orEmpty())
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun setupMediaChips() {
        binding.mediaChipsGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            when (checkedIds.firstOrNull()) {
                R.id.chipRadio -> viewModel.onMediaKindFilter(StreamsViewModel.MediaKindFilter.AUDIO)
                R.id.chipVideo -> viewModel.onMediaKindFilter(StreamsViewModel.MediaKindFilter.VIDEO)
                R.id.chipMine -> viewModel.onMediaKindFilter(StreamsViewModel.MediaKindFilter.OWN)
                R.id.chipRecent -> viewModel.onMediaKindFilter(StreamsViewModel.MediaKindFilter.RECENT)
                else -> viewModel.onMediaKindFilter(StreamsViewModel.MediaKindFilter.ALL)
            }
        }
    }

    private fun setupSleepTimer() {
        binding.btnSleepTimer.setOnClickListener {
            com.sza.streamsplayer.ui.streams.SleepTimerBottomSheet.newInstance()
                .show(supportFragmentManager, com.sza.streamsplayer.ui.streams.SleepTimerBottomSheet.TAG)
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                sleepTimerManager.timerState.collectLatest { state ->
                    when (state) {
                        is com.sza.streamsplayer.core.playback.SleepTimerState.Active -> {
                            val formatted = com.sza.streamsplayer.core.playback.SleepTimerPlan.formatRemaining(state.remainingMillis)
                            binding.btnSleepTimer.text = formatted
                            binding.btnSleepTimer.contentDescription = getString(R.string.sleep_timer_set_desc, formatted)
                        }
                        com.sza.streamsplayer.core.playback.SleepTimerState.Inactive -> {
                            binding.btnSleepTimer.text = ""
                            binding.btnSleepTimer.contentDescription = getString(R.string.sleep_timer)
                        }
                    }
                }
            }
        }
    }

    private fun setupRecyclerView() {
        channelAdapter = StreamChannelAdapter(
            scope = lifecycleScope,
            slicer = atlasSlicer,
            coordsProvider = { faviconCoords },
            onItemClick = { channel -> handleChannelClick(channel) },
            onPinClick = { channel -> viewModel.onPin(channel) },
            onMoreClick = { channel -> showChannelActions(channel) },
            onLiveClick = { row -> viewModel.onLiveSelected(row) },
            onLiveSave = { row -> saveLive(row) },
            previewTileLoader = { url -> previewCoords[url]?.let { previewSlicer.tileFor(it) } },
            logoTileLoader = { url -> logoCoords[url]?.let { logoSlicer.tileFor(it) } },
            hasArtworkTile = { channel ->
                logoCoords.containsKey(channel.url) ||
                    (usesPreviewTier(channel.mediaKind, channel.url) && previewCoords.containsKey(channel.url))
            },
        )
        binding.channelsRecyclerView.adapter = channelAdapter
    }

    private fun handleChannelClick(channel: StreamSourceEntity) {
        val isAudio = channel.mediaKind.equals("AUDIO", ignoreCase = true)
        if (isAudio) {
            inlineAudioManager.play(channel, useBackgroundService = true)
        } else {
            // Stop inline radio and launch fullscreen video/RTSP player
            inlineAudioManager.stop()
            val intent = FullscreenPlayerActivity.createIntent(this, channel)
            startActivity(intent)
        }
    }

    private fun showChannelActions(channel: StreamSourceEntity) {
        val sheet = StreamChannelActionsSheet.newInstance(channel)
        sheet.show(supportFragmentManager, StreamChannelActionsSheet.TAG)
    }

    private fun showFilterBottomSheet() {
        val sheet = StreamFilterBottomSheet()
        sheet.show(supportFragmentManager, StreamFilterBottomSheet.TAG)
    }

    // S0035: a dialog title with the help action in its end corner (CONTEXT-HELP rule 2).
    private fun helpDialogTitle(@androidx.annotation.StringRes title: Int, surfaceId: String): android.view.View {
        val titleView = layoutInflater.inflate(R.layout.view_dialog_title_help, null)
        titleView.findViewById<android.widget.TextView>(R.id.tvDialogTitle).setText(title)
        val helpButton = titleView.findViewById<android.view.View>(R.id.btnDialogHelp)
        androidx.appcompat.widget.TooltipCompat.setTooltipText(helpButton, HelpTooltipManager.tooltipText(this))
        helpButton.setOnClickListener { HelpOpenManager.open(this, surfaceId) }
        return titleView
    }

    private fun showImportPlaylistDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_import_playlist, null)
        val etUrl = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etPlaylistUrl)
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setCustomTitle(helpDialogTitle(R.string.import_playlist_title, HelpSurfaceIds.IMPORT_PLAYLIST))
            .setView(dialogView)
            .setPositiveButton(R.string.import_playlist_button) { _, _ ->
                val url = etUrl.text?.toString().orEmpty().trim()
                if (url.isNotEmpty()) {
                    viewModel.onImportPlaylist(url)
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .create()
            .also { dialog ->
                HelpKeys.install(dialog, HelpSurfaceIds.IMPORT_PLAYLIST)
                dialog.show()
            }
    }

    private fun showImportBroadcastDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_import_broadcast, null)
        val etPayload = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etBroadcastPayload)
        val btnFile = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnChooseBroadcastFile)
        val btnScan = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnScanBroadcastQr)

        val dialog = com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setCustomTitle(helpDialogTitle(R.string.import_broadcast_title, HelpSurfaceIds.IMPORT_BROADCAST))
            .setView(dialogView)
            .setPositiveButton(R.string.import_broadcast_button) { _, _ ->
                val payload = etPayload.text?.toString().orEmpty().trim()
                if (payload.isNotEmpty()) {
                    importBroadcast(payload)
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .create()

        btnFile.setOnClickListener {
            dialog.dismiss()
            importBroadcastFileLauncher.launch(arrayOf("*/*"))
        }

        btnScan.setOnClickListener {
            dialog.dismiss()
            importBroadcastQrLauncher.launch(Unit)
        }

        HelpKeys.install(dialog, HelpSurfaceIds.IMPORT_BROADCAST)
        dialog.show()
    }

    private fun readAndImportBroadcastUri(uri: android.net.Uri, keepAsChannel: Boolean = true) {
        lifecycleScope.launch {
            val content = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    contentResolver.openInputStream(uri)?.use {
                        com.sza.streamsplayer.data.broadcast.BroadcastDescriptorInput.read(it)
                    }.orEmpty()
                } catch (e: java.io.IOException) {
                    Timber.d("S0020: broadcast file could not be read")
                    ""
                } catch (e: SecurityException) {
                    Timber.d("S0020: broadcast file access denied")
                    ""
                }
            }
            if (content.isBlank()) {
                Toast.makeText(this@MainActivity, R.string.import_broadcast_invalid, Toast.LENGTH_SHORT).show()
            } else {
                if (keepAsChannel) importBroadcast(content) else broadcastLinkOpener.open(this@MainActivity, content)
            }
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.state.collectLatest { state ->
                        channelAdapter.submitRows(state.rows)
                        binding.emptyTextView.isVisible = state.isEmpty
                        binding.emptyTextView.setText(
                            if (state.own != null && state.own.filtered) R.string.own_filtered_empty else R.string.no_channels_found,
                        )
                        binding.loadingProgressBar.isVisible = state.isLoading || state.isImporting
                        renderOwnStatus(state.own)
                        renderCatalogOffer(state.catalogOffer)

                        // Update layout manager
                        val isGrid = state.displayMode == DisplayMode.GRID
                        channelAdapter.isGrid = isGrid
                        val currentLm = binding.channelsRecyclerView.layoutManager
                        if (isGrid && currentLm !is GridLayoutManager) {
                            binding.channelsRecyclerView.layoutManager = GridLayoutManager(this@MainActivity, 2).apply {
                                spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                                    override fun getSpanSize(position: Int): Int =
                                        if (position < channelAdapter.itemCount && channelAdapter.isFullWidth(position)) 2 else 1
                                }
                            }
                        } else if (!isGrid && (currentLm !is LinearLayoutManager || currentLm is GridLayoutManager)) {
                            binding.channelsRecyclerView.layoutManager = LinearLayoutManager(this@MainActivity)
                        }

                        // Update toggle menu icon
                        val toggleItem = binding.topToolbar.menu.findItem(R.id.menu_mode_toggle)
                        if (toggleItem != null) {
                            toggleItem.setIcon(if (isGrid) R.drawable.ic_list else R.drawable.ic_grid)
                            toggleItem.setTitle(if (isGrid) R.string.view_list else R.string.view_grid)
                        }

                        // Update active filter banner
                        val hasFilters = state.filter.hasActiveFilters
                        binding.activeFiltersBanner.isVisible = hasFilters
                        val selectedChip = when (state.filter.mediaKind) {
                            StreamsViewModel.MediaKindFilter.AUDIO -> R.id.chipRadio
                            StreamsViewModel.MediaKindFilter.VIDEO -> R.id.chipVideo
                            StreamsViewModel.MediaKindFilter.OWN -> R.id.chipMine
                            StreamsViewModel.MediaKindFilter.RECENT -> R.id.chipRecent
                            else -> R.id.chipAll
                        }
                        if (binding.mediaChipsGroup.checkedChipId != selectedChip) binding.mediaChipsGroup.check(selectedChip)
                        if (binding.etSearch.text.toString() != state.filter.query) binding.etSearch.setText(state.filter.query)
                        if (hasFilters) {
                            val activeDescriptions = listOfNotNull(
                                state.filter.category,
                                state.filter.topic,
                                state.filter.language,
                                state.filter.country,
                                state.filter.query.takeIf { it.isNotBlank() },
                                state.collections.find { it.id == state.filter.collectionId }?.let {
                                    com.sza.streamsplayer.ui.streams.helpers.StreamCollectionNameResolver.resolve(it.id, it.namesJson, resources.configuration.locales[0])
                                },
                                if (state.filter.pinnedOnly) getString(R.string.pinned_only) else null,
                            ).joinToString(", ")
                            binding.tvActiveFiltersSummary.text = activeDescriptions.ifEmpty {
                                getString(R.string.filter_and_sort)
                            }
                        }
                    }
                }

                launch {
                    viewModel.events.collectLatest { event ->
                        when (event) {
                            is StreamsViewModel.StreamsEvent.Message -> {
                                Toast.makeText(this@MainActivity, event.messageResId, Toast.LENGTH_SHORT).show()
                            }
                            is StreamsViewModel.StreamsEvent.CatalogUpdated -> {
                                Toast.makeText(
                                    this@MainActivity,
                                    getString(R.string.catalog_updated_summary, event.added, event.updated, event.removed),
                                    Toast.LENGTH_LONG,
                                ).show()
                                loadFaviconCoords()
                                // S1154/S1201/S1481: offer the preview payload, then the logo payload once
                                // the first offer is settled, so the two dialogs queue instead of stacking.
                                previewPromptManager.maybeOffer(this@MainActivity) {
                                    logoPromptManager.maybeOffer(this@MainActivity)
                                }
                            }
                            is StreamsViewModel.StreamsEvent.PlaylistImported -> {
                                Toast.makeText(
                                    this@MainActivity,
                                    getString(R.string.import_playlist_success, event.inserted),
                                    Toast.LENGTH_LONG,
                                ).show()
                            }
                            is StreamsViewModel.StreamsEvent.PlaylistImportFailed -> {
                                Toast.makeText(
                                    this@MainActivity,
                                    getString(R.string.import_playlist_failed, event.reason),
                                    Toast.LENGTH_LONG,
                                ).show()
                            }
                            is StreamsViewModel.StreamsEvent.PlayRequested -> {
                                handleChannelClick(event.source)
                            }
                            is StreamsViewModel.StreamsEvent.PlayLive -> {
                                inlineAudioManager.stop()
                                startActivity(
                                    Intent(this@MainActivity, FullscreenPlayerActivity::class.java).apply {
                                        putExtra(FullscreenPlayerActivity.EXTRA_CHANNEL_URL, event.url)
                                        putExtra(FullscreenPlayerActivity.EXTRA_CHANNEL_TITLE, event.title)
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    private fun loadFaviconCoords() {
        lifecycleScope.launch {
            // A catalog import rewrote the atlas with the index map: a sheet decoded before it would
            // answer the new indices with the old pictures.
            atlasSlicer.invalidate()
            faviconCoords = faviconStore.coords()
            previewCoords = channelPreviewStore.coords()
            logoCoords = streamLogoStore.coords()
            logStreamArtworkState()
            channelAdapter.notifyDataSetChanged()
        }
    }

    /** S1154: the payload just landed on disk, and the index map above was read before it existed. */
    private suspend fun reloadPreviewTiles() {
        previewSlicer.invalidate()
        previewCoords = channelPreviewStore.coords()
        logStreamArtworkState()
        channelAdapter.notifyDataSetChanged()
    }

    /** S1201: same reload for the logo payload - it is downloaded and installed independently. */
    private suspend fun reloadLogoTiles() {
        logoSlicer.invalidate()
        logoCoords = streamLogoStore.coords()
        logStreamArtworkState()
        channelAdapter.notifyDataSetChanged()
    }

    /**
     * One line that answers "why is there no artwork?" from a device log alone: which payload is on disk
     * and how many channels each index covers. Without it, an empty map and a missing file look the same.
     */
    private suspend fun logStreamArtworkState() {
        // S3230: the payload probes each stat a file, so they run on IO before the line is built.
        val previewKind = channelPreviewStore.payloadKind()
        val logoKind = streamLogoStore.payloadKind()
        Timber.i(
            "Streams artwork: favicon coords=%d, preview=%s/%d, logo=%s/%d (payload on disk / channels covered)",
            faviconCoords.size,
            previewKind,
            previewCoords.size,
            logoKind,
            logoCoords.size,
        )
    }

    private fun restoreBackgroundRadio() {
        if (!com.sza.streamsplayer.ui.player.AudioPlaybackService.isRunning) return
        audioController.connectForStatus { player ->
            val item = player?.currentMediaItem ?: return@connectForStatus
            val url = item.requestMetadata.mediaUri?.toString() ?: item.localConfiguration?.uri?.toString()
                ?: return@connectForStatus
            lifecycleScope.launch {
                val source = streamRepository.getByUrl(url) ?: StreamSourceEntity(
                    id = url, url = url, title = item.mediaMetadata.title?.toString() ?: url,
                    mediaKind = "AUDIO", sourceOrigin = "MANUAL", sortIndex = 0, addedAt = 0,
                )
                if (player.currentMediaItem == item) inlineAudioManager.attach(source, player)
            }
        }
    }

    override fun onDestroy() {
        broadcastPasswordPrompt.close()
        super.onDestroy()
        inlineAudioManager.release()
        _binding = null
    }
}
