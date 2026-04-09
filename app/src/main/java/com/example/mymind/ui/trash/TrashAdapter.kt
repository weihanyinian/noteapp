package com.example.mymind.ui.trash

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.mymind.databinding.ItemTrashBinding
import com.example.mymind.util.UiFormatters

/** 通用回收站条目（笔记和思维导图共用） */
data class TrashItem(
    val id: Long,
    val title: String,
    val deleteTime: Long?,
    val type: Type
) {
    enum class Type { NOTE, MIND_MAP }
}

class TrashAdapter(
    private val onRestore: (TrashItem) -> Unit,
    private val onDelete: (TrashItem) -> Unit
) : ListAdapter<TrashItem, TrashAdapter.TrashViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TrashViewHolder {
        val binding = ItemTrashBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return TrashViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TrashViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class TrashViewHolder(private val binding: ItemTrashBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(item: TrashItem) {
            val typeLabel = if (item.type == TrashItem.Type.NOTE) "[笔记]" else "[思维导图]"
            binding.itemTitle.text = "$typeLabel ${item.title}"
            binding.itemDeleteTime.text = if (item.deleteTime != null) {
                "删除于 ${UiFormatters.formatTime(item.deleteTime)}，${calcDaysLeft(item.deleteTime)} 天后永久删除"
            } else {
                "删除时间未知"
            }
            binding.restoreButton.setOnClickListener { onRestore(item) }
            binding.deleteButton.setOnClickListener { onDelete(item) }
        }

        private fun calcDaysLeft(deleteTime: Long): Long {
            val elapsed = System.currentTimeMillis() - deleteTime
            val daysElapsed = elapsed / (1000L * 60 * 60 * 24)
            return maxOf(0L, 15L - daysElapsed)
        }
    }

    companion object {
        private val DiffCallback = object : DiffUtil.ItemCallback<TrashItem>() {
            override fun areItemsTheSame(o: TrashItem, n: TrashItem) = o.id == n.id && o.type == n.type
            override fun areContentsTheSame(o: TrashItem, n: TrashItem) = o == n
        }
    }
}
