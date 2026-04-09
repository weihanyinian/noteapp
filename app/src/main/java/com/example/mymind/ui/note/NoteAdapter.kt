package com.example.mymind.ui.note

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.PopupMenu
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.mymind.data.local.entity.NoteEntity
import com.example.mymind.databinding.ItemNoteBinding
import com.example.mymind.util.UiFormatters

class NoteAdapter(
    private val onClick: (NoteEntity) -> Unit,
    private val onMoveToTrash: (NoteEntity) -> Unit
) : ListAdapter<NoteEntity, NoteAdapter.NoteViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NoteViewHolder {
        val binding = ItemNoteBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return NoteViewHolder(binding)
    }

    override fun onBindViewHolder(holder: NoteViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class NoteViewHolder(
        private val binding: ItemNoteBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: NoteEntity) {
            binding.titleText.text = item.title
            binding.previewText.text = UiFormatters.htmlToPlainText(item.content).ifBlank { "点击继续编写内容" }
            binding.updatedAtText.text = UiFormatters.formatTime(item.updatedAt)
            binding.root.setOnClickListener { onClick(item) }

            binding.menuButton.setOnClickListener { anchor ->
                val menu = PopupMenu(anchor.context, anchor)
                val trashId = 1
                menu.menu.add(0, trashId, 0, "删除")
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
    }

    companion object {
        private val DiffCallback = object : DiffUtil.ItemCallback<NoteEntity>() {
            override fun areItemsTheSame(oldItem: NoteEntity, newItem: NoteEntity): Boolean = oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: NoteEntity, newItem: NoteEntity): Boolean = oldItem == newItem
        }
    }
}
