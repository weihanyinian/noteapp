package com.example.mymind.ui.mindmap

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.updateLayoutParams
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.mymind.data.local.entity.MindNodeEntity
import com.example.mymind.databinding.ItemMindNodeBinding

class MindNodePreviewAdapter(
    private val onNodeClick: (MindNodeEntity) -> Unit,
    private val onNodeLongClick: (MindNodeEntity) -> Unit
) : ListAdapter<MindNodeEntity, MindNodePreviewAdapter.NodeViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NodeViewHolder {
        val binding = ItemMindNodeBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return NodeViewHolder(binding)
    }

    override fun onBindViewHolder(holder: NodeViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class NodeViewHolder(
        private val binding: ItemMindNodeBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: MindNodeEntity) {
            binding.nodeText.text = item.content
            binding.depthText.text = when {
                item.isRoot -> "中心节点"
                item.noteId != null -> "📝 层级 ${item.depth}"
                else -> "层级 ${item.depth}"
            }
            val marginStartPx = (item.depth * 24 * binding.root.resources.displayMetrics.density).toInt()
            binding.root.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                marginStart = marginStartPx
            }
            binding.root.setOnClickListener { onNodeClick(item) }
            binding.root.setOnLongClickListener {
                onNodeLongClick(item)
                true
            }
        }
    }

    companion object {
        private val DiffCallback = object : DiffUtil.ItemCallback<MindNodeEntity>() {
            override fun areItemsTheSame(oldItem: MindNodeEntity, newItem: MindNodeEntity): Boolean = oldItem.id == newItem.id
            override fun areContentsTheSame(oldItem: MindNodeEntity, newItem: MindNodeEntity): Boolean = oldItem == newItem
        }
    }
}
