package com.sza.streamsplayer.ui.exchange

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.sza.streamsplayer.R
import com.sza.streamsplayer.databinding.ItemExchangeBroadcastBinding
import com.sza.streamsplayer.databinding.ItemExchangeGroupBinding
import com.sza.streamsplayer.ui.exchange.helpers.ExchangeMessageText
import com.sza.streamsplayer.ui.exchange.model.DeviceBroadcastGroup
import com.sza.streamsplayer.ui.exchange.model.ExchangeBroadcastItem

sealed interface DirectoryListItem {
    data class DeviceHeader(val group: DeviceBroadcastGroup) : DirectoryListItem
    data class Broadcast(val item: ExchangeBroadcastItem, val deviceId: String) : DirectoryListItem
}

class ExchangeDirectoryAdapter(
    private val onPlayClick: (ExchangeBroadcastItem) -> Unit,
    private val onKeepClick: (ExchangeBroadcastItem) -> Unit,
    /** Shown after the mode on every broadcast row, e.g. "through Google Drive" (S0029); null for the server's list. */
    private val sourceLabel: String? = null,
    /** False for a source whose broadcasts are not kept as channels (S0029: the Drive list has no keep action). */
    private val showKeep: Boolean = true,
) : ListAdapter<DirectoryListItem, RecyclerView.ViewHolder>(DiffCallback) {

    override fun getItemViewType(position: Int): Int = when (getItem(position)) {
        is DirectoryListItem.DeviceHeader -> TYPE_HEADER
        is DirectoryListItem.Broadcast -> TYPE_BROADCAST
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_HEADER -> {
                val binding = ItemExchangeGroupBinding.inflate(inflater, parent, false)
                HeaderViewHolder(binding)
            }
            TYPE_BROADCAST -> {
                val binding = ItemExchangeBroadcastBinding.inflate(inflater, parent, false)
                BroadcastViewHolder(binding)
            }
            else -> throw IllegalArgumentException("Unknown viewType $viewType")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is DirectoryListItem.DeviceHeader -> (holder as HeaderViewHolder).bind(item.group)
            is DirectoryListItem.Broadcast -> (holder as BroadcastViewHolder).bind(item.item)
        }
    }

    fun submitGroups(groups: List<DeviceBroadcastGroup>) {
        val flatList = mutableListOf<DirectoryListItem>()
        for (group in groups) {
            flatList.add(DirectoryListItem.DeviceHeader(group))
            for (broadcast in group.broadcasts) {
                flatList.add(DirectoryListItem.Broadcast(broadcast, group.deviceId))
            }
        }
        submitList(flatList)
    }

    class HeaderViewHolder(
        private val binding: ItemExchangeGroupBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(group: DeviceBroadcastGroup) {
            binding.tvDeviceName.text = group.deviceName
            binding.tvDevicePresence.text = if (group.isOnline) {
                binding.root.context.getString(R.string.exchange_device_presence_online)
            } else {
                binding.root.context.getString(R.string.exchange_device_presence_offline)
            }
            val bgColor = if (group.isOnline) 0xFF2E7D32.toInt() else 0xFF757575.toInt()
            binding.tvDevicePresence.background.setTint(bgColor)
        }
    }

    inner class BroadcastViewHolder(
        private val binding: ItemExchangeBroadcastBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: ExchangeBroadcastItem) {
            binding.tvBroadcastTitle.text = item.title
            val context = binding.root.context
            val mode = ExchangeBroadcastModes.labelRes(item.mode)
                ?.let { context.getString(R.string.exchange_broadcast_mode, context.getString(it)) }
            val sealNotice = item.sealNotice?.let { context.getString(ExchangeMessageText.what(it)) }
            binding.tvBroadcastMode.text = listOfNotNull(mode, sourceLabel, sealNotice).joinToString(" - ")
            binding.btnPlay.contentDescription = binding.root.context.getString(R.string.exchange_broadcast_play_label, item.title)
            binding.btnKeep.contentDescription = binding.root.context.getString(R.string.exchange_broadcast_keep_label, item.title)
            binding.btnPlay.nextFocusForwardId = binding.btnKeep.id

            binding.btnPlay.isEnabled = item.isPlayable
            binding.btnPlay.setOnClickListener { onPlayClick(item) }

            binding.btnKeep.visibility = if (showKeep) android.view.View.VISIBLE else android.view.View.GONE
            if (!showKeep) return
            if (item.isKept) {
                binding.btnKeep.isEnabled = false
                binding.btnKeep.text = binding.root.context.getString(R.string.exchange_channel_kept)
            } else {
                binding.btnKeep.isEnabled = item.isPlayable
                binding.btnKeep.text = binding.root.context.getString(R.string.exchange_action_keep)
                binding.btnKeep.setOnClickListener { onKeepClick(item) }
            }
        }
    }

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_BROADCAST = 1

        private val DiffCallback = object : DiffUtil.ItemCallback<DirectoryListItem>() {
            override fun areItemsTheSame(oldItem: DirectoryListItem, newItem: DirectoryListItem): Boolean =
                when {
                    oldItem is DirectoryListItem.DeviceHeader && newItem is DirectoryListItem.DeviceHeader ->
                        oldItem.group.deviceId == newItem.group.deviceId
                    oldItem is DirectoryListItem.Broadcast && newItem is DirectoryListItem.Broadcast ->
                        oldItem.item.broadcastId == newItem.item.broadcastId
                    else -> false
                }

            override fun areContentsTheSame(oldItem: DirectoryListItem, newItem: DirectoryListItem): Boolean =
                oldItem == newItem
        }
    }
}
