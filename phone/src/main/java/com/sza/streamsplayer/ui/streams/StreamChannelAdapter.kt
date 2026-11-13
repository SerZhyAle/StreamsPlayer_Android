package com.sza.streamsplayer.ui.streams

import android.graphics.Bitmap
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.sza.streamsplayer.R
import com.sza.streamsplayer.core.streams.FaviconAtlasSlicer
import com.sza.streamsplayer.data.local.db.StreamSourceEntity
import com.sza.streamsplayer.databinding.ItemStreamChannelBinding
import com.sza.streamsplayer.databinding.ItemStreamDeviceBinding
import com.sza.streamsplayer.databinding.ItemStreamLiveBinding
import com.sza.streamsplayer.databinding.ItemStreamLiveEmptyBinding
import com.sza.streamsplayer.databinding.ItemStreamSectionBinding
import com.sza.streamsplayer.ui.streams.own.LiveBroadcast
import com.sza.streamsplayer.ui.streams.own.LiveKind
import com.sza.streamsplayer.ui.streams.own.LivePresence
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class StreamChannelAdapter(
    private val scope: CoroutineScope,
    private val slicer: FaviconAtlasSlicer,
    private val coordsProvider: () -> Map<String, Int>,
    private val onItemClick: (StreamSourceEntity) -> Unit,
    private val onPinClick: (StreamSourceEntity) -> Unit,
    private val onMoreClick: (StreamSourceEntity) -> Unit,
    private val onLiveClick: (LiveBroadcast) -> Unit = {},
    private val onLiveSave: (LiveBroadcast) -> Unit = {},
    // S0050: the two on-demand tiers above the favicon; an install with neither payload answers null.
    private val previewTileLoader: suspend (String) -> Bitmap? = { null },
    private val logoTileLoader: suspend (String) -> Bitmap? = { null },
    private val hasArtworkTile: (StreamSourceEntity) -> Boolean = { false },
) : ListAdapter<StreamListRow, RecyclerView.ViewHolder>(RowDiffCallback) {

    var activePlayingId: String? = null
        set(value) {
            val old = field
            field = value
            if (old != value) notifyDataSetChanged()
        }

    var isGrid: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            notifyDataSetChanged()
        }

    var isActivelyPlaying: Boolean = false
        set(value) {
            val old = field
            field = value
            if (old != value) notifyDataSetChanged()
        }

    /**
     * S0070: submits the rows the screen shows. A change that touches more than [RowDiffBudget.MAX_CHANGED_ROWS]
     * rows (a search over the whole catalog, clearing it, another category) replaces the list at once, which also
     * drops any diff still queued from an earlier keystroke; a smaller change is diffed in place as before.
     */
    fun submitRows(rows: List<StreamListRow>) {
        if (!RowDiffBudget.withinBudget(currentList, rows, RowDiffCallback::areItemsTheSame)) submitList(null)
        submitList(rows)
    }

    /** Whether the row at [position] spans the whole width in grid mode (everything but a channel card). */
    fun isFullWidth(position: Int): Boolean = getItem(position) !is StreamListRow.Channel

    fun showFocusedChannelActions(recyclerView: RecyclerView, focus: View?): Boolean {
        val row = focus?.let(recyclerView::findContainingViewHolder) ?: return false
        val position = row.bindingAdapterPosition
        if (position == RecyclerView.NO_POSITION) return false
        val item = getItem(position) as? StreamListRow.Channel ?: return false
        onMoreClick(item.source)
        return true
    }

    override fun getItemViewType(position: Int): Int = when (getItem(position)) {
        is StreamListRow.Channel -> TYPE_CHANNEL
        is StreamListRow.Section -> TYPE_SECTION
        is StreamListRow.Device -> TYPE_DEVICE
        is StreamListRow.Live -> TYPE_LIVE
        StreamListRow.LiveEmpty -> TYPE_LIVE_EMPTY
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_SECTION -> SectionViewHolder(ItemStreamSectionBinding.inflate(inflater, parent, false))
            TYPE_DEVICE -> DeviceViewHolder(ItemStreamDeviceBinding.inflate(inflater, parent, false))
            TYPE_LIVE -> LiveViewHolder(ItemStreamLiveBinding.inflate(inflater, parent, false))
            TYPE_LIVE_EMPTY -> LiveEmptyViewHolder(ItemStreamLiveEmptyBinding.inflate(inflater, parent, false))
            else -> ChannelViewHolder(ItemStreamChannelBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is StreamListRow.Channel -> (holder as ChannelViewHolder).bind(item.source)
            is StreamListRow.Section -> (holder as SectionViewHolder).bind(item)
            is StreamListRow.Device -> (holder as DeviceViewHolder).bind(item)
            is StreamListRow.Live -> (holder as LiveViewHolder).bind(item.broadcast)
            StreamListRow.LiveEmpty -> Unit
        }
    }

    private class SectionViewHolder(private val binding: ItemStreamSectionBinding) : RecyclerView.ViewHolder(binding.root) {
        init {
            // minSdk 26 predates the layout attribute (API 28); the compat call covers both.
            androidx.core.view.ViewCompat.setAccessibilityHeading(binding.tvSectionTitle, true)
        }

        fun bind(row: StreamListRow.Section) {
            binding.tvSectionTitle.setText(
                when (row.kind) {
                    StreamListRow.SectionKind.LIVE -> R.string.own_section_live
                    StreamListRow.SectionKind.SAVED -> R.string.own_section_saved
                },
            )
        }
    }

    private class DeviceViewHolder(private val binding: ItemStreamDeviceBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(row: StreamListRow.Device) {
            val context = binding.root.context
            binding.tvDeviceName.text = row.name ?: context.getString(R.string.own_device_unnamed)
            binding.tvDevicePresence.setText(presenceLabel(row.presence))
        }
    }

    private class LiveEmptyViewHolder(binding: ItemStreamLiveEmptyBinding) : RecyclerView.ViewHolder(binding.root)

    private inner class LiveViewHolder(private val binding: ItemStreamLiveBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(broadcast: LiveBroadcast) {
            val context = binding.root.context
            val title = broadcast.title ?: context.getString(R.string.own_live_untitled)
            val device = broadcast.deviceName ?: context.getString(R.string.own_device_unnamed)
            val kind = when (broadcast.kind) {
                LiveKind.AUDIO -> context.getString(R.string.own_kind_audio)
                LiveKind.VIDEO -> context.getString(R.string.own_kind_video)
                null -> null
            }
            val presence = context.getString(presenceLabel(broadcast.presence))
            val details = listOfNotNull(device, kind, presence)
            binding.tvLiveTitle.text = StreamTitleFormatter.display(title)
            binding.tvLiveSubtitle.text = details.joinToString(" • ")
            binding.ivLiveIcon.setImageResource(if (broadcast.kind == LiveKind.VIDEO) R.drawable.ic_video else R.drawable.ic_radio)
            binding.tvLiveSaved.isVisible = broadcast.saved != null
            binding.tvLiveUnsupported.isVisible = !broadcast.playable
            binding.btnLiveSave.isVisible = broadcast.saved == null
            binding.btnLiveSave.contentDescription = context.getString(R.string.own_live_save_desc, title)
            binding.root.contentDescription = buildString {
                append(title).append(", ").append(details.joinToString(", "))
                if (broadcast.saved != null) append(", ").append(context.getString(R.string.own_live_saved_badge))
                if (!broadcast.playable) append(", ").append(context.getString(R.string.own_live_unsupported_row))
            }
            binding.root.alpha = if (broadcast.playable) 1f else UNSUPPORTED_ALPHA
            binding.root.setOnClickListener { onLiveClick(broadcast) }
            binding.btnLiveSave.setOnClickListener { onLiveSave(broadcast) }
        }
    }

    inner class ChannelViewHolder(
        private val binding: ItemStreamChannelBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        private var iconLoadJob: Job? = null

        fun bind(channel: StreamSourceEntity) {
            iconLoadJob?.cancel()
            val context = binding.root.context
            binding.channelContent.orientation = if (isGrid) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
            binding.channelDetails.layoutParams = LinearLayout.LayoutParams(
                if (isGrid) ViewGroup.LayoutParams.MATCH_PARENT else 0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                if (isGrid) 0f else 1f,
            )
            binding.channelActions.layoutParams = LinearLayout.LayoutParams(
                if (isGrid) ViewGroup.LayoutParams.MATCH_PARENT else ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )

            binding.tvChannelTitle.text = StreamTitleFormatter.display(channel.title)

            val details = listOfNotNull(
                channel.category?.takeIf { it.isNotBlank() },
                channel.topic?.takeIf { it.isNotBlank() },
                channel.language?.takeIf { it.isNotBlank() },
                channel.country?.takeIf { it.isNotBlank() },
            ).joinToString(" • ")
            binding.tvChannelSubtitle.text = details.ifEmpty { channel.url }

            val isAudio = channel.mediaKind.equals("AUDIO", ignoreCase = true)
            binding.tvMediaBadge.text = when {
                channel.sourceOrigin == "MANUAL" -> context.getString(R.string.filter_mine).uppercase()
                isAudio -> context.getString(R.string.filter_radio).uppercase()
                channel.mediaKind.equals("RTSP", ignoreCase = true) -> "RTSP"
                else -> context.getString(R.string.filter_video).uppercase()
            }

            val isPlayingThis = channel.id == activePlayingId && isActivelyPlaying
            binding.ivPlayingIndicator.isVisible = isPlayingThis

            binding.btnPin.setImageResource(
                if (channel.pinned) R.drawable.ic_pin else R.drawable.ic_pin_outline,
            )
            binding.btnPin.contentDescription = context.getString(
                if (channel.pinned) R.string.action_unpin else R.string.action_pin,
            )

            // Stand-in icon per ICON-EXTERNAL
            val defaultIconRes = if (isAudio) R.drawable.ic_radio else R.drawable.ic_video
            bindArtworkFrame(expanded = isGrid && hasArtworkTile(channel))
            binding.ivChannelIcon.scaleType = ImageView.ScaleType.CENTER_INSIDE
            binding.ivChannelIcon.setImageResource(defaultIconRes)

            // STREAM-BANK document 09 section 5: preview, logo, favicon, each miss falling to the next.
            iconLoadJob = scope.launch {
                val artwork = resolveChannelArtwork(
                    mediaKind = channel.mediaKind,
                    url = channel.url,
                    preview = { previewTileLoader(channel.url) },
                    logo = { logoTileLoader(channel.url) },
                    favicon = { coordsProvider()[channel.url]?.let { slicer.tileFor(it) } },
                ) ?: return@launch
                withContext(Dispatchers.Main) {
                    binding.ivChannelIcon.scaleType = scaleTypeOf(artwork.tier)
                    binding.ivChannelIcon.setImageBitmap(artwork.picture)
                }
            }

            binding.root.setOnClickListener { onItemClick(channel) }
            binding.btnPin.setOnClickListener { onPinClick(channel) }
            binding.btnMoreActions.setOnClickListener { onMoreClick(channel) }
        }

        /**
         * A grid card with a tile of its own gives the picture the card's width; every other row keeps
         * the icon box it always had. Decided from the in-memory index at bind time, so the card never
         * changes height when the decode lands.
         */
        private fun bindArtworkFrame(expanded: Boolean) {
            val resources = binding.root.resources
            val iconSize = resources.getDimensionPixelSize(R.dimen.stream_channel_icon_size)
            val gap = resources.getDimensionPixelSize(R.dimen.stream_channel_icon_gap)
            val width = if (expanded) ViewGroup.LayoutParams.MATCH_PARENT else iconSize
            val height = if (expanded) resources.getDimensionPixelSize(R.dimen.stream_grid_artwork_height) else iconSize
            val frameParams = LinearLayout.LayoutParams(width, height)
            if (expanded) {
                frameParams.bottomMargin = gap
            } else {
                frameParams.marginEnd = gap
            }
            binding.channelArtworkFrame.layoutParams = frameParams
            binding.ivChannelIcon.layoutParams = FrameLayout.LayoutParams(width, height)
        }
    }

    private object RowDiffCallback : DiffUtil.ItemCallback<StreamListRow>() {
        // S0070: two channel rows are the same item when their ids are equal, which is what their keys say, without
        // building two strings per comparison on a diff that makes millions of them.
        override fun areItemsTheSame(oldItem: StreamListRow, newItem: StreamListRow): Boolean =
            if (oldItem is StreamListRow.Channel && newItem is StreamListRow.Channel) {
                oldItem.source.id == newItem.source.id
            } else {
                oldItem.key == newItem.key
            }

        override fun areContentsTheSame(oldItem: StreamListRow, newItem: StreamListRow): Boolean =
            oldItem == newItem
    }

    companion object {
        private const val TYPE_CHANNEL = 0
        private const val TYPE_SECTION = 1
        private const val TYPE_DEVICE = 2
        private const val TYPE_LIVE = 3
        private const val TYPE_LIVE_EMPTY = 4
        private const val UNSUPPORTED_ALPHA = 0.6f

        /** A preview bleeds to the box; a logo keeps its own transparent margins; a favicon keeps its scale. */
        private fun scaleTypeOf(tier: ChannelArtworkTier): ImageView.ScaleType = when (tier) {
            ChannelArtworkTier.PREVIEW -> ImageView.ScaleType.CENTER_CROP
            ChannelArtworkTier.LOGO -> ImageView.ScaleType.FIT_CENTER
            ChannelArtworkTier.FAVICON -> ImageView.ScaleType.CENTER_INSIDE
        }

        private fun presenceLabel(presence: LivePresence): Int = when (presence) {
            LivePresence.ONLINE -> R.string.own_presence_online
            LivePresence.OFFLINE -> R.string.own_presence_offline
            LivePresence.UNKNOWN -> R.string.own_presence_unknown
        }
    }
}
