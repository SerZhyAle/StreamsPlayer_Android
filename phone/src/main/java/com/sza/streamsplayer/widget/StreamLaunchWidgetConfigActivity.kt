package com.sza.streamsplayer.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.sza.streamsplayer.R
import com.sza.streamsplayer.core.streams.FaviconAtlasSlicer
import com.sza.streamsplayer.data.local.db.StreamSourceEntity
import com.sza.streamsplayer.data.repository.streams.FaviconAtlasStore
import com.sza.streamsplayer.databinding.ActivityStreamLaunchWidgetConfigBinding
import com.sza.streamsplayer.ui.common.help.HelpKeys
import com.sza.streamsplayer.ui.common.help.HelpOpenManager
import com.sza.streamsplayer.ui.common.help.HelpSurfaceIds
import com.sza.streamsplayer.ui.common.help.HelpTooltipManager
import com.sza.streamsplayer.databinding.ItemWidgetConfigChannelBinding
import com.sza.streamsplayer.domain.usecase.streams.ObserveStreamSourcesUseCase
import com.sza.streamsplayer.ui.streams.RowDiffBudget
import com.sza.streamsplayer.ui.streams.helpers.StreamWidgetResolveManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

/**
 * S1916 - the `APPWIDGET_CONFIGURE` screen the system opens when a stream tile is placed.
 */
@AndroidEntryPoint
class StreamLaunchWidgetConfigActivity : AppCompatActivity() {

    @Inject
    lateinit var resolveManager: StreamWidgetResolveManager

    @Inject
    lateinit var observeStreams: ObserveStreamSourcesUseCase

    @Inject
    lateinit var faviconAtlasStore: FaviconAtlasStore

    private var _binding: ActivityStreamLaunchWidgetConfigBinding? = null
    private val binding get() = checkNotNull(_binding)

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private lateinit var adapter: ConfigChannelAdapter
    private var allChannels: List<StreamSourceEntity> = emptyList()
    private var atlasCoords: Map<String, Int> = emptyMap()
    private val faviconSlicer by lazy { FaviconAtlasSlicer { faviconAtlasStore.atlasFile() } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        _binding = ActivityStreamLaunchWidgetConfigBinding.inflate(layoutInflater)
        setContentView(binding.root)

        appWidgetId = intent?.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        setResult(RESULT_CANCELED, resultIntent())

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            Timber.w("StreamLaunchWidgetConfigActivity: started without a widget id")
            finish()
            return
        }

        setupViews()
        loadChannels()
    }

    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        if (HelpKeys.handle(this, HelpSurfaceIds.WIDGET_SETUP, event)) return true
        return super.dispatchKeyEvent(event)
    }

    private fun setupViews() {
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }
        // S0035: the help action (CONTEXT-HELP rule 2), the only item the bar carries besides its close icon.
        binding.toolbar.inflateMenu(R.menu.menu_help)
        HelpTooltipManager.apply(this, binding.toolbar.menu.findItem(R.id.menu_help))
        binding.toolbar.setOnMenuItemClickListener { item ->
            if (item.itemId == R.id.menu_help) {
                HelpOpenManager.open(this, HelpSurfaceIds.WIDGET_SETUP)
                true
            } else {
                false
            }
        }

        adapter = ConfigChannelAdapter(
            onChannelClick = { channel -> applyPick(channel) },
            loadIcon = { channel ->
                atlasCoords[channel.url]?.let { index -> faviconSlicer.tileFor(index) }
            },
        )
        binding.rvChannels.layoutManager = LinearLayoutManager(this)
        binding.rvChannels.adapter = adapter

        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterChannels(s?.toString().orEmpty())
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })
    }

    private fun loadChannels() {
        binding.progressBar.isVisible = true
        lifecycleScope.launch {
            allChannels = observeStreams().first()
            atlasCoords = withContext(Dispatchers.IO) { faviconAtlasStore.coords() }
            binding.progressBar.isVisible = false
            filterChannels(binding.etSearch.text?.toString().orEmpty())
        }
    }

    private fun filterChannels(query: String) {
        val trimmed = query.trim()
        val filtered = if (trimmed.isEmpty()) {
            allChannels
        } else {
            allChannels.filter { it.title.contains(trimmed, ignoreCase = true) }
        }
        // S0071: the same defect class as the catalog screen (S0070). A search over the whole catalog would queue a
        // DiffUtil run per keystroke; a change over the budget replaces the list at once, which also drops a diff
        // still queued from an earlier keystroke. Keyed by id, as DiffCallback below.
        val withinBudget = RowDiffBudget.withinBudget(adapter.currentList, filtered) { old, new -> old.id == new.id }
        if (!withinBudget) adapter.submitList(null)
        adapter.submitList(filtered)
        binding.tvEmptyState.isVisible = filtered.isEmpty()
    }

    private fun applyPick(channel: StreamSourceEntity) {
        lifecycleScope.launch {
            binding.progressBar.isVisible = true
            val resolved = resolveManager.resolve(channel.identityKey)
            withContext(Dispatchers.IO) {
                StreamLaunchWidgetStore.save(
                    context = applicationContext,
                    appWidgetId = appWidgetId,
                    url = channel.url,
                    title = channel.title,
                    mediaKind = channel.mediaKind,
                    iconTile = resolved?.icon,
                )
                StreamLaunchWidgetProvider.updateAppWidget(
                    applicationContext,
                    AppWidgetManager.getInstance(applicationContext),
                    appWidgetId,
                )
            }
            setResult(RESULT_OK, resultIntent())
            finish()
        }
    }

    private fun resultIntent(): Intent =
        Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }

    companion object {
        fun createIntent(context: Context, appWidgetId: Int): Intent =
            Intent(context, StreamLaunchWidgetConfigActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            }
    }

    private class ConfigChannelAdapter(
        private val onChannelClick: (StreamSourceEntity) -> Unit,
        private val loadIcon: suspend (StreamSourceEntity) -> android.graphics.Bitmap?,
    ) : ListAdapter<StreamSourceEntity, ConfigChannelAdapter.ViewHolder>(DiffCallback) {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemWidgetConfigChannelBinding.inflate(
                LayoutInflater.from(parent.context), parent, false,
            )
            return ViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(getItem(position))
        }

        inner class ViewHolder(private val binding: ItemWidgetConfigChannelBinding) :
            RecyclerView.ViewHolder(binding.root) {

            fun bind(channel: StreamSourceEntity) {
                binding.tvChannelTitle.text = channel.title
                val subtitleParts = listOfNotNull(
                    channel.mediaKind.takeIf { it.isNotBlank() },
                    (channel.topic ?: channel.category)?.takeIf { it.isNotBlank() },
                )
                binding.tvChannelSubtitle.text = subtitleParts.joinToString(" • ")
                binding.tvChannelSubtitle.isVisible = subtitleParts.isNotEmpty()
                binding.ivChannelIcon.setImageResource(R.drawable.ic_stream)

                (itemView.context as? AppCompatActivity)?.lifecycleScope?.launch {
                    val bitmap = loadIcon(channel)
                    if (bitmap != null) {
                        binding.ivChannelIcon.setImageBitmap(bitmap)
                    }
                }

                binding.root.setOnClickListener { onChannelClick(channel) }
            }
        }

        private object DiffCallback : DiffUtil.ItemCallback<StreamSourceEntity>() {
            override fun areItemsTheSame(oldItem: StreamSourceEntity, newItem: StreamSourceEntity) =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: StreamSourceEntity, newItem: StreamSourceEntity) =
                oldItem == newItem
        }
    }
}
