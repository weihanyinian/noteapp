package com.example.mymind.ui.trash

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.mymind.R
import com.example.mymind.MainActivity
import com.example.mymind.databinding.ActivityTrashBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayout

class TrashActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTrashBinding
    private val viewModel: TrashViewModel by viewModels()
    private var returnDestination: String = MainActivity.DEST_RECENT

    private val trashAdapter by lazy {
        TrashAdapter(
            onRestore = { item ->
                viewModel.restore(item)
            },
            onDelete = { item ->
                MaterialAlertDialogBuilder(this)
                    .setTitle("永久删除")
                    .setMessage("确认永久删除「${item.title}」？此操作不可恢复。")
                    .setPositiveButton("删除") { _, _ -> viewModel.permanentDelete(item) }
                    .setNegativeButton("取消", null)
                    .show()
            }
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTrashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        returnDestination = intent.getStringExtra(EXTRA_RETURN_DESTINATION) ?: MainActivity.DEST_RECENT
        binding.topAppBar.setNavigationOnClickListener {
            startActivity(
                Intent(this, MainActivity::class.java)
                    .putExtra(MainActivity.EXTRA_DESTINATION, returnDestination)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            )
            finish()
        }
        binding.topAppBar.setOnMenuItemClickListener { item ->
            if (item.itemId == R.id.action_empty_trash) {
                confirmEmptyTrash()
                true
            } else false
        }

        binding.trashRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@TrashActivity)
            adapter = trashAdapter
        }

        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                viewModel.setTab(tab.position)
            }
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })

        viewModel.trashItems.observe(this) { items ->
            trashAdapter.submitList(items)
            binding.emptyView.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    private fun confirmEmptyTrash() {
        MaterialAlertDialogBuilder(this)
            .setTitle("清空回收站")
            .setMessage("确认清空当前标签下的所有回收站内容？此操作不可恢复。")
            .setPositiveButton("清空") { _, _ -> viewModel.emptyCurrentTab() }
            .setNegativeButton("取消", null)
            .show()
    }

    companion object {
        private const val EXTRA_RETURN_DESTINATION = "extra_return_destination"

        fun createIntent(context: Context, returnDestination: String): Intent {
            return Intent(context, TrashActivity::class.java).apply {
                putExtra(EXTRA_RETURN_DESTINATION, returnDestination)
            }
        }
    }
}
