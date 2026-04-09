package com.example.mymind.ui.mindmap

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.mymind.MyMindApplication
import com.example.mymind.databinding.ActivityTemplateBinding
import com.example.mymind.databinding.ItemTemplateBinding
import kotlinx.coroutines.launch

class TemplateActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTemplateBinding
    private val repository by lazy { (application as MyMindApplication).repository }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTemplateBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.topAppBar.setNavigationOnClickListener { finish() }

        val adapter = TemplateAdapter { template ->
            lifecycleScope.launch {
                val id = repository.createDefaultMindMap(title = template.title)
                startActivity(MindMapDetailActivity.createIntent(this@TemplateActivity, id))
                finish()
            }
        }
        binding.templateRecyclerView.layoutManager = GridLayoutManager(this, 4)
        binding.templateRecyclerView.adapter = adapter
        adapter.submitList(
            listOf(
                TemplateItem("思维导图"),
                TemplateItem("组织结构图"),
                TemplateItem("树形图"),
                TemplateItem("时间轴"),
                TemplateItem("鱼骨图"),
                TemplateItem("表格"),
                TemplateItem("复盘"),
                TemplateItem("清单")
            )
        )
    }

    data class TemplateItem(val title: String)

    class TemplateAdapter(
        private val onClick: (TemplateItem) -> Unit
    ) : ListAdapter<TemplateItem, TemplateAdapter.VH>(Diff) {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val binding = ItemTemplateBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return VH(binding, onClick)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            holder.bind(getItem(position))
        }

        class VH(
            private val binding: ItemTemplateBinding,
            private val onClick: (TemplateItem) -> Unit
        ) : RecyclerView.ViewHolder(binding.root) {
            fun bind(item: TemplateItem) {
                binding.titleText.text = item.title
                binding.root.setOnClickListener { onClick(item) }
            }
        }

        companion object {
            private val Diff = object : DiffUtil.ItemCallback<TemplateItem>() {
                override fun areItemsTheSame(oldItem: TemplateItem, newItem: TemplateItem): Boolean = oldItem.title == newItem.title
                override fun areContentsTheSame(oldItem: TemplateItem, newItem: TemplateItem): Boolean = oldItem == newItem
            }
        }
    }

    companion object {
        fun createIntent(context: Context): Intent = Intent(context, TemplateActivity::class.java)
    }
}

