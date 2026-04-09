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
            binding.previewCenterText.text = item.rootNodeTitle
            binding.titleText.text = item.title
            val branchTexts = buildBranchTexts(item)
            binding.branch1Text.text = branchTexts.getOrNull(0) ?: "分支"
            binding.branch2Text.text = branchTexts.getOrNull(1) ?: "节点"
            binding.branch3Text.text = branchTexts.getOrNull(2) ?: "灵感"
            binding.branch4Text.text = branchTexts.getOrNull(3) ?: "结构"
            binding.updatedAtText.text = UiFormatters.formatTime(item.updatedAt)
            binding.root.setOnClickListener { onClick(item) }
        }
    }

    private fun buildBranchTexts(item: MindMapEntity): List<String> {
        val base = (item.title + " " + item.rootNodeTitle)
            .replace("\\s+".toRegex(), " ")
            .trim()
        if (base.isBlank()) return emptyList()
        val tokens = base.split(" ")
            .flatMap { token ->
                if (token.length <= 2) listOf(token)
                else listOf(token.take(2), token.drop(2).take(2)).filter { it.isNotBlank() }
            }
            .filter { it.isNotBlank() }
        return tokens.take(4)
    }

    private object Diff : DiffUtil.ItemCallback<MindMapEntity>() {
        override fun areItemsTheSame(oldItem: MindMapEntity, newItem: MindMapEntity): Boolean = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: MindMapEntity, newItem: MindMapEntity): Boolean = oldItem == newItem
    }
}

