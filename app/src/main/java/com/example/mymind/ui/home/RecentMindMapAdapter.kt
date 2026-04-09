package com.example.mymind.ui.home

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.mymind.data.local.entity.MindMapEntity
import com.example.mymind.databinding.ItemRecentMindMapBinding
import com.example.mymind.util.UiFormatters

class RecentMindMapAdapter(
    private val onClick: (MindMapEntity) -> Unit
) : ListAdapter<MindMapEntity, RecentMindMapAdapter.VH>(Diff) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemRecentMindMapBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(getItem(position))
    }

    inner class VH(
        private val binding: ItemRecentMindMapBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: MindMapEntity) {
            binding.centerText.text = item.rootNodeTitle
            binding.titleText.text = item.title
            binding.updatedAtText.text = UiFormatters.formatTime(item.updatedAt)
            binding.root.setOnClickListener { onClick(item) }
        }
    }

    private object Diff : DiffUtil.ItemCallback<MindMapEntity>() {
        override fun areItemsTheSame(oldItem: MindMapEntity, newItem: MindMapEntity): Boolean = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: MindMapEntity, newItem: MindMapEntity): Boolean = oldItem == newItem
    }
}

