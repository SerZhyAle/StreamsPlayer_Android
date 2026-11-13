package com.sza.streamsplayer.ui.settings

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.os.Trace
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import android.widget.ScrollView
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.OneShotPreDrawListener
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.children
import androidx.core.view.doOnLayout
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sza.streamsplayer.R
import com.sza.streamsplayer.core.util.LocaleHelper
import com.sza.streamsplayer.core.util.ThemeModeStore
import com.sza.streamsplayer.core.util.UiLanguageCatalog
import com.sza.streamsplayer.databinding.ActivitySettingsBinding
import com.sza.streamsplayer.databinding.PageSettingsBroadcastBinding
import com.sza.streamsplayer.databinding.PageSettingsGeneralBinding
import com.sza.streamsplayer.distribution.Distribution
import com.sza.streamsplayer.domain.delivery.DeliverableSet
import com.sza.streamsplayer.domain.delivery.ExtensionItem
import com.sza.streamsplayer.domain.model.StreamsCatalogRefreshPolicy
import com.sza.streamsplayer.domain.repository.SettingsRepository
import com.sza.streamsplayer.ui.broadcast.helpers.BroadcastSettingsPanelManager
import com.sza.streamsplayer.ui.common.help.HelpKeys
import com.sza.streamsplayer.ui.common.help.HelpOpenManager
import com.sza.streamsplayer.ui.common.help.HelpSurfaceIds
import com.sza.streamsplayer.ui.common.help.HelpTooltipManager
import com.sza.streamsplayer.ui.common.widget.SettingsArtworkRow
import com.sza.streamsplayer.ui.settings.about.SettingsAboutDestinationView
import com.sza.streamsplayer.ui.settings.artwork.ChannelPictureRowState
import com.sza.streamsplayer.ui.settings.artwork.ChannelPicturesViewModel
import com.sza.streamsplayer.util.showBoundToHost
import com.sza.streamsplayer.utils.collectOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * The settings screen (SETTINGS-NAVIGATION 2.0): the shared rail at reading start and one page at reading end.
 *
 * From 600 dp of width the rail stays beside the page. Below that the rail is a dedicated navigation view: the page
 * list fills the window, a destination opens its page in the same window, and Up returns to the list with the selection
 * kept (section 2.6; recorded in `docs/design/UI_SIZE_PROFILE.md`).
 *
 * A page is built once, on its first visit, from values read before it is inflated, and then kept for the screen's
 * life, so it is never shown half-filled and its scroll position survives a switch (sections 4.3 and 5.1).
 */
@AndroidEntryPoint
class SettingsActivity : AppCompatActivity() {

    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var broadcastPanelManager: BroadcastSettingsPanelManager
    @Inject lateinit var distribution: Distribution

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var screenStore: SettingsScreenStore
    private val channelPictures: ChannelPicturesViewModel by viewModels()

    private class Page(val root: View, val scroll: ScrollView)

    private val pages = mutableMapOf<String, Page>()
    private var flushBroadcastPanel: (() -> Unit)? = null

    /** The page being prepared; a newer activation cancels it, so an old result never replaces the current page. */
    private var pageWork: Job? = null
    private var selectedId = SettingsDestinations.GENERAL

    /** Below the breakpoint: whether the window shows the page and not the list. Beside the rail it is always shown. */
    private var pageOpen = false

    /** A screen opened at a named page (Broadcast Control's button) goes back to its caller, not to the page list. */
    private var openedAtNamedPage = false
    private var firstPageReady = false

    private val twoPane: Boolean
        get() = resources.configuration.screenWidthDp >= TWO_PANE_MIN_WIDTH_DP

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        screenStore = SettingsScreenStore(this)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val sysBars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout() or
                    WindowInsetsCompat.Type.ime()
            )
            view.setPadding(sysBars.left, sysBars.top, sysBars.right, sysBars.bottom)
            insets
        }

        setupToolbar()
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = navigateBack()
        })

        val named = intent.getStringExtra(EXTRA_DESTINATION)
        val lastPage = screenStore.lastPage
        openedAtNamedPage = named != null
        selectedId = SettingsDestinations.resolve(
            savedInstanceState?.getString(STATE_PAGE) ?: named ?: lastPage
        ).id

        binding.settingsRail.setDestinations(SettingsDestinations.ALL)
        binding.settingsRail.selectDestination(selectedId)
        binding.settingsRail.setOnDestinationSelectedListener { openPage(it.id) }

        if (!twoPane) binding.settingsRailPane.updateLayoutParams { width = ViewGroup.LayoutParams.MATCH_PARENT }
        renderPanes()

        // A later opening lands on the page left last (section 4.3); only the very first one starts at the list.
        val reopening = savedInstanceState == null && lastPage != null
        if (twoPane || named != null || reopening || savedInstanceState?.getBoolean(STATE_PAGE_OPEN) == true) {
            holdFirstFrame()
            openPage(selectedId)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.getStringExtra(EXTRA_DESTINATION)?.let { named ->
            openedAtNamedPage = true
            openPage(SettingsDestinations.resolve(named).id)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_PAGE, selectedId)
        outState.putBoolean(STATE_PAGE_OPEN, pageOpen)
    }

    override fun onPause() {
        // A text row commits when it is left, and leaving the screen leaves the row focused (S3234).
        flushBroadcastPanel?.invoke()
        pages.forEach { (id, page) -> captureViewport(page.scroll)?.let { screenStore.saveViewport(id, it) } }
        super.onPause()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (HelpKeys.handle(this, helpSurface(), event)) return true
        return super.dispatchKeyEvent(event)
    }

    // F1 and the help action answer for the page on the screen (CONTEXT-HELP rule 3).
    private fun helpSurface(): String =
        if (pageOpen && selectedId == SettingsDestinations.BROADCAST) {
            HelpSurfaceIds.BROADCAST_SETTINGS
        } else {
            HelpSurfaceIds.SETTINGS
        }

    private fun setupToolbar() {
        binding.settingsToolbar.setNavigationOnClickListener { navigateBack() }
        binding.settingsToolbar.inflateMenu(R.menu.menu_help)
        HelpTooltipManager.apply(this, binding.settingsToolbar.menu.findItem(R.id.menu_help))
        binding.settingsToolbar.setOnMenuItemClickListener { item ->
            if (item.itemId == R.id.menu_help) {
                HelpOpenManager.open(this, helpSurface())
                true
            } else {
                false
            }
        }
    }

    private fun navigateBack() {
        if (!twoPane && pageOpen && !openedAtNamedPage) {
            pageOpen = false
            renderPanes()
            binding.settingsRail.focusSelected()
        } else {
            finish()
        }
    }

    private fun renderPanes() {
        binding.settingsRailPane.isVisible = twoPane || !pageOpen
        binding.settingsPagePane.isVisible = twoPane || pageOpen
    }

    private fun openPage(id: String) {
        Timber.d("S0049: settings page activated")
        val activation = SystemClock.elapsedRealtimeNanos()
        pageWork?.cancel()
        selectedId = id
        screenStore.lastPage = id
        binding.settingsRail.selectDestination(id)
        pageWork = lifecycleScope.launch {
            val firstVisit = id !in pages
            val page = pages[id] ?: buildPage(id).also { pages[id] = it }
            pages.values.forEach { it.root.isVisible = it === page }
            binding.settingsPageTitle.setText(SettingsDestinations.resolve(id).titleRes)
            pageOpen = true
            renderPanes()
            firstPageReady = true
            traceUntilFrame(id, firstVisit, activation)
        }
    }

    /**
     * Keeps the window from drawing until the first page is ready, so the screen never opens on an empty pane (section
     * 5.1). The limit is a guard against a read that never returns: then the shell draws and the page follows.
     */
    private fun holdFirstFrame() {
        val observer = binding.root.viewTreeObserver
        observer.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                if (!firstPageReady) return false
                binding.root.viewTreeObserver.removeOnPreDrawListener(this)
                return true
            }
        })
        binding.root.postDelayed({ firstPageReady = true }, FIRST_FRAME_HOLD_LIMIT_MS)
    }

    /**
     * The activation-to-frame timing of section 5.4: an async trace section a profiler reads, and a debug log line. It
     * ends on the traversal after the page's pre-draw, which is the frame that shows it.
     */
    private fun traceUntilFrame(id: String, firstVisit: Boolean, activation: Long) {
        val kind = if (firstVisit) "first" else "warm"
        val section = "settings.page.$id.$kind"
        val cookie = activation.toInt()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) Trace.beginAsyncSection(section, cookie)
        val root = binding.root
        OneShotPreDrawListener.add(root) {
            root.post {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) Trace.endAsyncSection(section, cookie)
                val elapsedMs = (SystemClock.elapsedRealtimeNanos() - activation) / NANOS_PER_MILLI
                Timber.d("Settings page %s %s visit: %d ms", id, kind, elapsedMs)
            }
        }
        root.invalidate()
    }

    private suspend fun buildPage(id: String): Page {
        val (root, scroll) = when (id) {
            SettingsDestinations.BROADCAST -> buildBroadcastPage()
            SettingsDestinations.ABOUT -> buildAboutPage()
            else -> buildGeneralPage()
        }
        root.isVisible = false
        binding.settingsContent.addView(
            root,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
        )
        screenStore.viewport(id)?.let { viewport -> scroll.doOnLayout { restoreViewport(scroll, viewport) } }
        return Page(root, scroll)
    }

    private suspend fun buildGeneralPage(): Pair<View, ScrollView> {
        val settings = settingsRepository.getSettings().first()
        val page = PageSettingsGeneralBinding.inflate(layoutInflater, binding.settingsContent, false)

        val modes = ThemeModeStore.Mode.entries
        page.rowTheme.setEntries(
            listOf(
                getString(R.string.settings_theme_system),
                getString(R.string.settings_theme_light),
                getString(R.string.settings_theme_dark),
            )
        )
        page.rowTheme.setSelection(ThemeModeStore.read(this).ordinal)
        page.rowTheme.setOnItemSelectedListener { index ->
            Timber.d("S0049: theme row chosen")
            val mode = modes[index]
            if (mode != ThemeModeStore.read(this)) ThemeModeStore.save(this, mode)
        }

        UiLanguageCatalog.ensureInitialized(this)
        val tags = UiLanguageCatalog.supportedTags
        page.rowLanguage.setEntries(
            listOf<CharSequence>(getString(R.string.settings_language_system)) + tags.map(UiLanguageCatalog::displayName)
        )
        var languageIndex = if (LocaleHelper.isFollowingSystemLanguage(this)) {
            0
        } else {
            tags.indexOf(LocaleHelper.getLanguage(this)).coerceAtLeast(0) + 1
        }
        page.rowLanguage.setSelection(languageIndex)
        page.rowLanguage.setOnItemSelectedListener { index ->
            Timber.d("S0049: language row chosen")
            if (index != languageIndex) {
                languageIndex = index
                val code = if (index == 0) LocaleHelper.FOLLOW_SYSTEM_LANGUAGE else tags[index - 1]
                LocaleHelper.applyLanguageLive(this, code)
            }
        }

        page.rowEnableBroadcasting.setCheckedSilently(settings.enableBroadcasting)
        page.rowEnableBroadcasting.setOnCheckedChangeListener(broadcastPanelManager::setBroadcastingEnabled)
        collectOnLifecycle(settingsRepository.getSettings()) { current ->
            if (page.rowEnableBroadcasting.isChecked != current.enableBroadcasting) {
                page.rowEnableBroadcasting.setCheckedSilently(current.enableBroadcasting)
            }
        }

        // S0068: the ordinal is the position in the list, as in the source's dropdown row.
        val policies = StreamsCatalogRefreshPolicy.entries
        page.rowCatalogRefresh.setEntries(
            listOf(
                getString(R.string.settings_streams_catalog_refresh_manual),
                getString(R.string.settings_streams_catalog_refresh_on_open),
                getString(R.string.settings_streams_catalog_refresh_wifi),
            )
        )
        page.rowCatalogRefresh.setSelection(settings.streamsCatalogRefreshPolicy.ordinal)
        page.rowCatalogRefresh.setOnItemSelectedListener { index ->
            Timber.d("S0068: catalog refresh row chosen")
            val policy = policies[index]
            lifecycleScope.launch {
                settingsRepository.updateSettings { latest ->
                    if (latest.streamsCatalogRefreshPolicy == policy) latest else latest.copy(streamsCatalogRefreshPolicy = policy)
                }
            }
        }

        bindChannelPictures(
            page.rowChannelPreviews,
            DeliverableSet.CHANNEL_PREVIEW_ATLAS,
            R.string.channel_pictures_previews_title,
            R.string.channel_pictures_remove_message_previews,
        )
        bindChannelPictures(
            page.rowChannelLogos,
            DeliverableSet.STREAM_LOGO_ATLAS,
            R.string.channel_pictures_logos_title,
            R.string.channel_pictures_remove_message_logos,
        )
        return page.root to page.root
    }

    /**
     * S0064: one channel-pictures row. The page is drawn with the row in its checking state; the status and
     * the published size fill in when they arrive, and nothing happens until a button is tapped.
     */
    private fun bindChannelPictures(
        row: SettingsArtworkRow,
        set: DeliverableSet,
        @StringRes titleRes: Int,
        @StringRes removeMessageRes: Int,
    ) {
        val item = channelPictures.extensions.filterIsInstance<ExtensionItem.Module>().first { it.set == set }
        var state = ChannelPictureRowState.Checking
        var megabytes: Int? = null
        row.setTitle(titleRes)
        row.render(state, megabytes)
        row.setOnActionClickListener { channelPictures.download(item) }
        row.setOnRemoveClickListener { confirmRemoveChannelPictures(item, removeMessageRes, megabytes) }
        collectOnLifecycle(item.statusFlow) { status ->
            state = ChannelPictureRowState.of(status)
            row.render(state, megabytes)
        }
        lifecycleScope.launch {
            megabytes = channelPictures.sizeMegabytes(set)
            row.render(state, megabytes)
        }
    }

    // The action is destructive, so the confirmation cannot be skipped and names what goes and how much it frees.
    private fun confirmRemoveChannelPictures(item: ExtensionItem.Module, @StringRes messageRes: Int, megabytes: Int?) {
        val message = getString(messageRes).let { text ->
            if (megabytes == null) text else text + "\n\n" + getString(R.string.channel_pictures_remove_size, megabytes)
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.channel_pictures_remove_title)
            .setMessage(message)
            .setPositiveButton(R.string.channel_pictures_action_remove) { _, _ -> channelPictures.uninstall(item) }
            .setNegativeButton(R.string.action_cancel, null)
            .showBoundToHost(this)
    }

    private suspend fun buildBroadcastPage(): Pair<View, ScrollView> {
        val settings = settingsRepository.getSettings().first()
        broadcastPanelManager.prepareFirstFrame()
        val page = PageSettingsBroadcastBinding.inflate(layoutInflater, binding.settingsContent, false)

        fun renderOff(enabled: Boolean) {
            page.broadcastOffNotice.isVisible = !enabled
            page.btnOpenGeneral.isVisible = !enabled
        }
        renderOff(settings.enableBroadcasting)
        page.btnOpenGeneral.setOnClickListener { openPage(SettingsDestinations.GENERAL) }
        broadcastPanelManager.bind(this, page.broadcastPanel)
        flushBroadcastPanel = { broadcastPanelManager.flushPending(page.broadcastPanel) }
        collectOnLifecycle(settingsRepository.getSettings()) { renderOff(it.enableBroadcasting) }
        return page.root to page.root
    }

    private fun buildAboutPage(): Pair<View, ScrollView> {
        val about = SettingsAboutDestinationView(this).apply {
            updatesUrl = distribution.updatesUrl
            onHelpClickListener = { startActivity(HelpOpenManager.indexIntent(this@SettingsActivity)) }
        }
        return about to (about.getChildAt(0) as ScrollView)
    }

    private fun captureViewport(scroll: ScrollView): PageViewport? {
        val column = scroll.getChildAt(0) as? ViewGroup ?: return null
        val anchor = column.children.firstOrNull {
            it.id != View.NO_ID && it.isVisible && it.bottom > scroll.scrollY
        } ?: return null
        return PageViewport(resources.getResourceEntryName(anchor.id), scroll.scrollY - anchor.top)
    }

    // ScrollView clamps what it is told, so a viewport from a longer language or a larger text size stays in range.
    private fun restoreViewport(scroll: ScrollView, viewport: PageViewport) {
        val column = scroll.getChildAt(0) as? ViewGroup ?: return
        val anchor = column.children.firstOrNull {
            it.id != View.NO_ID && resources.getResourceEntryName(it.id) == viewport.anchorName
        } ?: return
        scroll.scrollTo(0, anchor.top + viewport.offsetPx)
    }

    companion object {
        private const val EXTRA_DESTINATION = "destination"
        private const val STATE_PAGE = "page"
        private const val STATE_PAGE_OPEN = "page_open"
        private const val TWO_PANE_MIN_WIDTH_DP = 600
        private const val FIRST_FRAME_HOLD_LIMIT_MS = 400L
        private const val NANOS_PER_MILLI = 1_000_000L

        /** Opens the screen; [destination] names a page of [SettingsDestinations], and Back then returns to the caller. */
        fun launch(context: Context, destination: String? = null) {
            val intent = Intent(context, SettingsActivity::class.java)
            destination?.let { intent.putExtra(EXTRA_DESTINATION, it) }
            context.startActivity(intent)
        }
    }
}
