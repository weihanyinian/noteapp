package com.example.mymind.ui.home

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.mymind.data.local.entity.NoteEntity
import com.example.mymind.databinding.ItemRecentNoteBinding
import com.example.mymind.util.UiFormatters

class RecentNoteAdapter(
    private val onClick: (NoteEntity) -> Unit
) : ListAdapter<NoteEntity, RecentNoteAdapter.VH>(Diff) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemRecentNoteBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(getItem(position))
    }

    inner class VH(
        private val binding: ItemRecentNoteBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: NoteEntity) {
            binding.titleText.text = item.title
            binding.previewText.text = UiFormatters.htmlToPlainText(item.content).take(80)
            binding.updatedAtText.text = UiFormatters.formatTime(item.updatedAt)
            binding.root.setOnClickListener { onClick(item) }
        }
    }

    private object Diff : DiffUtil.ItemCallback<NoteEntity>() {
        override fun areItemsTheSame(oldItem: NoteEntity, newItem: NoteEntity): Boolean = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: NoteEntity, newItem: NoteEntity): Boolean = oldItem == newItem
    }
}

