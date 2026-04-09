package com.example.mymind.ui.mindmap

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.PopupMenu
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.mymind.data.local.entity.MindMapEntity
import com.example.mymind.databinding.ItemMindMapBinding
import com.example.mymind.util.UiFormatters

class MindMapAdapter(
    private val onClick: (MindMapEntity) -> Unit,
    private val onMoveToTrash: (MindMapEntity) -> Unit
) : ListAdapter<MindMapEntity, MindMapAdapter.MindMapViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MindMapViewHolder {
        val binding = ItemMindMapBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return MindMapViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MindMapViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class MindMapViewHolder(
        private val binding: ItemMindMapBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: MindMapEntity) {
            binding.titleText.text = item.title
            binding.previewCenterText.text = item.rootNodeTitle
            val branchTexts = buildBranchTexts(item)
            binding.branch1Text.text = branchTexts.getOrNull(0) ?: "分支"
            binding.branch2Text.text = branchTexts.getOrNull(1) ?: "节点"
            binding.branch3Text.text = branchTexts.getOrNull(2) ?: "灵感"
            binding.branch4Text.text = branchTexts.getOrNull(3) ?: "结构"
            binding.updatedAtText.text = UiFormatters.formatTime(item.updatedAt)
            binding.root.setOnClickListener { onClick(item) }
            binding.root.setOnLongClickListener {
                onMoveToTrash(item)
                true
            }
            binding.menuButton.setOnClickListener { anchor ->
                val menu = PopupMenu(anchor.context, anchor)
                val renameId = 1
                val trashId = 2
                menu.menu.add(0, renameId, 0, "重命名")
                menu.menu.add(0, trashId, 1, "移到回收站")
                menu.setOnMenuItemClickListener { mi ->
                    when (mi.itemId) {
                        trashId -> {
                            onMoveToTrash(item)
                            true
                        }
                        else -> false
                    }
                }
                menu.show()
            }
        }

        private fun buildBranchTexts(item: MindMapEntity): List<String> {
            val base = (item.title + " " + item.rootNodeTitle)
                .replace("\\s+".toRegex(), " ")
                .trim()
            if (base.isBlank()) return emptyList()
            val tokens = base.split(" ")
                .flatMap { token ->
                    if (token.length <= 2) listOf(token) else listOf(token.take(2), token.drop(2).take(2)).filter { it.isNotBlank() }
                }
                .filter { it.isNotBlank() }
            return tokens.take(4)
        }
    }

    companion object {
        private val DiffCallback = object : DiffUtil.ItemCallback<MindMapEntity>() {
            override fun areItemsTheSame(oldItem: MindMapEntity, newItem: MindMapEntity): Boolean = oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: MindMapEntity, newItem: MindMapEntity): Boolean = oldItem == newItem
        }
    }
}
