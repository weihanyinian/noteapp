package com.example.mymind.ui.note

import android.os.Bundle
import android.widget.EditText
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.GridLayoutManager
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

    private val pdfPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            requireContext().contentResolver.takePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            val intent = NoteEditorActivity.createIntent(requireContext(), null).apply {
                putExtra("import_pdf_uri", uri.toString())
            }
            startActivity(intent)
        }
    }

    private val imagePicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            requireContext().contentResolver.takePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            val intent = NoteEditorActivity.createIntent(requireContext(), null).apply {
                putExtra("import_image_uri", uri.toString())
            }
            startActivity(intent)
        }
    }

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
            layoutManager = GridLayoutManager(requireContext(), if (isTablet) 4 else 2)
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
            showCreateMenu()
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
            "导入图片",
            "导入备份笔记"
        )
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("新增")
            .setItems(items) { _, which ->
                when (which) {
                    0 -> startActivity(NoteEditorActivity.createIntent(requireContext(), null))
                    2 -> pdfPicker.launch(arrayOf("application/pdf"))
                    3 -> imagePicker.launch(arrayOf("image/*"))
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance(): NoteListFragment = NoteListFragment()
    }
}
