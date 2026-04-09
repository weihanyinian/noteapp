package com.example.mymind.ui.mindmap

import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.mymind.R
import com.example.mymind.databinding.FragmentMindMapListBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.snackbar.Snackbar

class MindMapListFragment : Fragment() {

    private var _binding: FragmentMindMapListBinding? = null
    private val binding get() = _binding!!
    private val viewModel: MindMapListViewModel by viewModels()

    private val mindMapAdapter by lazy {
        MindMapAdapter(
            onClick = { mindMap ->
                startActivity(MindMapDetailActivity.createIntent(requireContext(), mindMap.id))
            },
            onMoveToTrash = { mindMap ->
                viewModel.trashMindMap(mindMap.id)
                Snackbar.make(binding.root, "已移至回收站", Snackbar.LENGTH_LONG)
                    .setAction(R.string.undo) { viewModel.restoreMindMap(mindMap.id) }
                    .show()
            }
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMindMapListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val isTablet = resources.configuration.smallestScreenWidthDp >= 600
        binding.mindMapRecyclerView.apply {
            layoutManager = if (isTablet) {
                LinearLayoutManager(requireContext())
            } else {
                GridLayoutManager(requireContext(), calculateSpanCount())
            }
            adapter = mindMapAdapter
        }

        // 滑动删除（移到回收站）
        if (!isTablet) {
            val swipeCallback = object : ItemTouchHelper.SimpleCallback(
                0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
            ) {
                override fun onMove(
                    rv: RecyclerView,
                    vh: RecyclerView.ViewHolder,
                    target: RecyclerView.ViewHolder
                ) = false

                override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                    val pos = viewHolder.bindingAdapterPosition
                    val mindMap = mindMapAdapter.currentList.getOrNull(pos)
                    if (mindMap == null) {
                        mindMapAdapter.notifyItemChanged(pos)
                        return
                    }
                    viewModel.trashMindMap(mindMap.id)
                    Snackbar.make(binding.root, "已移至回收站", Snackbar.LENGTH_LONG)
                        .setAction(R.string.undo) {
                            viewModel.restoreMindMap(mindMap.id)
                        }
                        .show()
                }
            }
            ItemTouchHelper(swipeCallback).attachToRecyclerView(binding.mindMapRecyclerView)
        }

        binding.addMindMapFab.setOnClickListener {
            if (isTablet) {
                showCreateMenu()
            } else {
                viewModel.createMindMap()
            }
        }

        viewModel.mindMaps.observe(viewLifecycleOwner) { mindMaps ->
            mindMapAdapter.submitList(mindMaps)
            binding.emptyView.visibility = if (mindMaps.isEmpty()) View.VISIBLE else View.GONE
        }

        viewModel.openMindMapEvent.observe(viewLifecycleOwner) { mindMapId ->
            if (mindMapId == null) return@observe
            startActivity(MindMapDetailActivity.createIntent(requireContext(), mindMapId))
            viewModel.consumeOpenMindMapEvent()
        }

        requireActivity().addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menuInflater.inflate(R.menu.menu_mind_map_list, menu)
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                return when (menuItem.itemId) {
                    R.id.action_search -> {
                        showSearchDialog()
                        true
                    }
                    R.id.action_filter -> true
                    R.id.action_more -> true
                    else -> false
                }
            }
        }, viewLifecycleOwner)
    }

    private fun showSearchDialog() {
        val input = TextInputEditText(requireContext()).apply {
            hint = getString(R.string.mind_map_search_hint)
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("搜索")
            .setView(input)
            .setPositiveButton("确定") { _, _ ->
                viewModel.setQuery(input.text?.toString().orEmpty())
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showCreateMenu() {
        val items = arrayOf("新建思维导图", "选择模板", "快捷输入", "新建文件夹")
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("新增")
            .setItems(items) { _, which ->
                when (which) {
                    0 -> viewModel.createMindMap()
                    1 -> startActivity(TemplateActivity.createIntent(requireContext()))
                    else -> Snackbar.make(binding.root, "待完善", Snackbar.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun calculateSpanCount(): Int {
        val sw = resources.configuration.smallestScreenWidthDp
        return if (sw >= 600) 1 else 2
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_TITLE = "arg_title"

        fun newInstance(title: String = "最近"): MindMapListFragment {
            return MindMapListFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_TITLE, title)
                }
            }
        }
    }
}
