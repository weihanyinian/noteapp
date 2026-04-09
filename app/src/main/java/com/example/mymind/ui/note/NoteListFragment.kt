package com.example.mymind.ui.note

import android.os.Bundle
import android.widget.EditText
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.mymind.MainActivity
import com.example.mymind.ui.trash.TrashActivity
import com.example.mymind.databinding.FragmentNoteListBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar

class NoteListFragment : Fragment() {

    private var _binding: FragmentNoteListBinding? = null
    private val binding get() = _binding!!
    private val viewModel: NoteListViewModel by viewModels()

    private val noteAdapter by lazy {
        NoteAdapter(
            onClick = { note ->
                startActivity(NoteEditorActivity.createIntent(requireContext(), note.id))
            },
            onMoveToTrash = { note ->
                viewModel.trashNote(note.id)
                Snackbar.make(binding.root, "已移至回收站", Snackbar.LENGTH_LONG)
                    .setAction("撤销") { viewModel.restoreNote(note.id) }
                    .show()
            }
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentNoteListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val isTablet = resources.configuration.smallestScreenWidthDp >= 600
        binding.noteRecyclerView.apply {
            layoutManager = if (isTablet) {
                GridLayoutManager(requireContext(), calculateTabletSpanCount())
            } else {
                LinearLayoutManager(requireContext())
            }
            adapter = noteAdapter
        }

        binding.searchButton.setOnClickListener { showSearchDialog() }
        binding.moreButton.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("笔记")
                .setItems(arrayOf("回收站")) { _, which ->
                    if (which == 0) {
                        startActivity(TrashActivity.createIntent(requireContext(), returnDestination = MainActivity.DEST_NOTES))
                    }
                }
                .setNegativeButton("取消", null)
                .show()
        }

        binding.chipRecentDeleted.setOnClickListener {
            startActivity(TrashActivity.createIntent(requireContext(), returnDestination = MainActivity.DEST_NOTES))
        }

        if (!isTablet) {
            val itemTouchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
                override fun onMove(
                    recyclerView: RecyclerView,
                    viewHolder: RecyclerView.ViewHolder,
                    target: RecyclerView.ViewHolder
                ): Boolean = false

                override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                    val position = viewHolder.bindingAdapterPosition
                    val note = noteAdapter.currentList.getOrNull(position)
                    if (note == null) {
                        noteAdapter.notifyItemChanged(position)
                        return
                    }
                    viewModel.trashNote(note.id)
                    Snackbar.make(binding.root, "已移至回收站", Snackbar.LENGTH_LONG)
                        .setAction("撤销") {
                            viewModel.restoreNote(note.id)
                        }
                        .show()
                }
            })
            itemTouchHelper.attachToRecyclerView(binding.noteRecyclerView)
        }
        binding.addNoteFab.setOnClickListener {
            if (isTablet) {
                showCreateMenu()
            } else {
                startActivity(NoteEditorActivity.createIntent(requireContext(), null))
            }
        }

        viewModel.notes.observe(viewLifecycleOwner) { notes ->
            noteAdapter.submitList(notes)
            binding.emptyView.visibility = if (notes.isEmpty()) View.VISIBLE else View.GONE
            binding.countText.text = "${notes.size} 篇笔记"
        }
    }
    private fun showCreateMenu() {
        val items = arrayOf(
            "新建笔记",
            "新建文件夹",
            "导入 PDF",
            "导入 PPT/Word/CAJ",
            "导入备份笔记",
            "快捷笔记",
            "无限草稿"
        )
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("新增")
            .setItems(items) { _, which ->
                when (which) {
                    0 -> startActivity(NoteEditorActivity.createIntent(requireContext(), null))
                    else -> Snackbar.make(binding.root, "待完善", Snackbar.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }


    private fun showSearchDialog() {
        val editText = EditText(requireContext()).apply { hint = "搜索标题或内容" }
        MaterialAlertDialogBuilder(requireContext())
            .setView(editText)
            .setPositiveButton("搜索") { _, _ ->
                viewModel.setQuery(editText.text?.toString().orEmpty())
            }
            .setNegativeButton("取消", null)
            .setNeutralButton("清除") { _, _ -> viewModel.setQuery("") }
            .show()
    }

    private fun calculateTabletSpanCount(): Int {
        val sw = resources.configuration.smallestScreenWidthDp
        return (sw / 220).coerceIn(2, 5)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance(): NoteListFragment = NoteListFragment()
    }
}
