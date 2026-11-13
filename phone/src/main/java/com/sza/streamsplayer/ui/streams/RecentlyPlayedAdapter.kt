package com.sza.streamsplayer.ui.streams

import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.sza.streamsplayer.R
import com.sza.streamsplayer.data.local.db.StreamSourceEntity
import com.sza.streamsplayer.databinding.ItemRecentlyPlayedChannelBinding

class RecentlyPlayedAdapter(
    private val onPlayClick: (StreamSourceEntity) -> Unit,
) : ListAdapter<StreamSourceEntity, RecentlyPlayedAdapter.ViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemRecentlyPlayedChannelBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false,
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(
        private val binding: ItemRecentlyPlayedChannelBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(source: StreamSourceEntity) {
            binding.tvChannelTitle.text = StreamTitleFormatter.display(source.title)

            val relativeTime = source.lastPlayedAt?.let {
                DateUtils.getRelativeTimeSpanString(
                    it,
                    System.currentTimeMillis(),
                    DateUtils.MINUTE_IN_MILLIS,
                    DateUtils.FORMAT_ABBREV_RELATIVE,
                ).toString()
            }
            binding.tvChannelSubtitle.text = relativeTime ?: source.url

            val isAudio = source.mediaKind.equals("AUDIO", ignoreCase = true)
            binding.ivChannelIcon.setImageResource(if (isAudio) R.drawable.ic_radio else R.drawable.ic_video)
            binding.tvMediaBadge.text = source.mediaKind.uppercase()

            binding.btnPlayRecent.setOnClickListener { onPlayClick(source) }
            binding.root.setOnClickListener { onPlayClick(source) }
        }
    }

    object DiffCallback : DiffUtil.ItemCallback<StreamSourceEntity>() {
        override fun areItemsTheSame(oldItem: StreamSourceEntity, newItem: StreamSourceEntity): Boolean =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: StreamSourceEntity, newItem: StreamSourceEntity): Boolean =
            oldItem == newItem
    }
}
