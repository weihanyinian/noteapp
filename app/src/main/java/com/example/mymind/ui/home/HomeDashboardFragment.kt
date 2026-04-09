package com.example.mymind.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.mymind.databinding.FragmentHomeDashboardBinding
import com.example.mymind.ui.mindmap.MindMapDetailActivity
import com.example.mymind.ui.note.NoteEditorActivity
import com.example.mymind.ui.note.NoteDetailActivity
import com.example.mymind.util.UiFormatters
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class HomeDashboardFragment : Fragment() {

    private var _binding: FragmentHomeDashboardBinding? = null
    private val binding get() = _binding!!

    private val viewModel: HomeDashboardViewModel by viewModels()

    private val mindMapAdapter by lazy {
        RecentMindMapAdapter { mindMap ->
            startActivity(MindMapDetailActivity.createIntent(requireContext(), mindMap.id))
        }
    }

    private val noteAdapter by lazy {
        RecentNoteAdapter { note ->
            startActivity(com.example.mymind.ui.note.NoteEditorActivity.createIntent(requireContext(), note.id))
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.recentMindMaps.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = mindMapAdapter
        }

        binding.recentNotes.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = noteAdapter
        }

        binding.searchInput.addTextChangedListener { editable ->
            viewModel.setQuery(editable?.toString().orEmpty())
        }

        binding.createFab.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("新建")
                .setItems(arrayOf("新建思维导图", "新建笔记")) { _, which ->
                    when (which) {
                        0 -> viewModel.createMindMap()
                        1 -> startActivity(NoteEditorActivity.createIntent(requireContext(), null))
                    }
                }
                .setNegativeButton("取消", null)
                .show()
        }

        viewModel.recentMindMaps.observe(viewLifecycleOwner) { list ->
            mindMapAdapter.submitList(list)
        }
        viewModel.recentNotes.observe(viewLifecycleOwner) { list ->
            noteAdapter.submitList(list)
        }

        viewModel.openMindMapEvent.observe(viewLifecycleOwner) { id ->
            if (id == null) return@observe
            startActivity(MindMapDetailActivity.createIntent(requireContext(), id))
            viewModel.consumeOpenMindMapEvent()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance(): HomeDashboardFragment = HomeDashboardFragment()
    }
}
