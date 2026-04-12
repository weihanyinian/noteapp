package com.example.mymind.ui.mindmap

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.PopupMenu
import android.widget.EditText
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.lifecycleScope
import com.example.mymind.MainActivity
import com.example.mymind.R
import com.example.mymind.data.local.entity.NoteEntity
import com.example.mymind.data.local.entity.MindNodeEntity
import com.example.mymind.data.local.relation.MindMapWithNodes
import com.example.mymind.databinding.ActivityMindMapDetailBinding
import com.example.mymind.ui.note.NoteEditorActivity
import com.example.mymind.util.UiFormatters
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 思维导图详情页：
 * - 顶部菜单：布局/分享/撤销/新增节点/样式/更多
 * - 画布：MindMapBoardView + ZoomPanLayout（缩放/平移）
 * - 底栏：常用操作（新增子节点、套索、自动布局、绑定笔记等）
 */
class MindMapDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMindMapDetailBinding
    private val viewModel: MindMapDetailViewModel by viewModels()

    private val nodeAdapter by lazy {
        MindNodePreviewAdapter(
            onNodeClick = { node ->
                binding.mindMapBoard.setSelectedNode(node.id)
                selectedNodeId = node.id
                focusNode(node.id)
                updateQuickAddChildFab()
            },
            onNodeLongClick = { node ->
                binding.mindMapBoard.setSelectedNode(node.id)
                selectedNodeId = node.id
                updateQuickAddChildFab()
                showNodeContextMenu(node)
            }
        )
    }

    private var mindMapId: Long = INVALID_MIND_MAP_ID
    private var focusNodeId: Long? = null
    private var isApplyingTitleFromDatabase = false
    private var allNotes: List<NoteEntity> = emptyList()
    private var notePreviewById: Map<Long, String> = emptyMap()
    private var latestNodes: List<MindNodeEntity> = emptyList()
    private var selectedNodeId: Long? = null
    private var titleSaveJob: Job? = null
    private var isLassoEnabled: Boolean = false
    private var hasFitOnce: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMindMapDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        mindMapId = intent.getLongExtra(EXTRA_MIND_MAP_ID, INVALID_MIND_MAP_ID)
        focusNodeId = intent.getLongExtra(EXTRA_FOCUS_NODE_ID, INVALID_NODE_ID).takeIf { it != INVALID_NODE_ID }
        if (mindMapId == INVALID_MIND_MAP_ID) {
            finish()
            return
        }

        setupToolbar()
        setupActions()
        setupMindMapCanvas()
        observeData()
        observeEvents()
        viewModel.loadMindMap(mindMapId)

        // 订阅所有笔记（用于"选择已有笔记"对话框）
        val noteDao = (application as com.example.mymind.MyMindApplication).database.noteDao()
        noteDao.observeAll().observe(this) { notes ->
            allNotes = notes
            notePreviewById = notes.associate { note ->
                note.id to UiFormatters.htmlToPlainText(note.content).take(40)
            }
            if (latestNodes.isNotEmpty()) {
                binding.mindMapBoard.submit(latestNodes, notePreviewById)
            }
        }
    }

    override fun onPause() {
        super.onPause()
    }

    private fun setupToolbar() {
        binding.topAppBar.setNavigationOnClickListener {
            finish()
        }
        binding.topAppBar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_share -> { shareMindMap(); true }
                R.id.action_undo -> { viewModel.undo(); true }
                R.id.action_add_node -> { showAddChildNodeDialog(selectedNodeId); true }
                R.id.action_style -> {
                    val node = getSelectedNode()
                    if (node != null) showStyleEntryDialog(node)
                    true
                }
                R.id.action_more -> { showMoreMenu(); true }
                else -> false
            }
        }
    }

    /** 解析 AI 返回的节点文本（每行一个节点，'-'表示子节点）并批量添加 */
    private fun parseAndAddAiNodes(raw: String) {
        val contents = raw.lines()
            .map { it.trimStart('-', ' ', '\t').trim() }
            .filter { it.isNotBlank() }

        if (contents.isEmpty()) {
            Toast.makeText(this, "AI 未生成有效节点", Toast.LENGTH_SHORT).show()
            return
        }
        lifecycleScope.launch {
            contents.forEach { content ->
                (application as com.example.mymind.MyMindApplication).repository
                    .addNode(mindMapId, content)
            }
            Toast.makeText(this@MindMapDetailActivity, "已添加 ${contents.size} 个节点", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showNodeListSheet() {
        val dialog = BottomSheetDialog(this)
        val sheetBinding = com.example.mymind.databinding.BottomSheetNodeListBinding.inflate(layoutInflater)
        sheetBinding.nodeRecyclerView.adapter = nodeAdapter
        dialog.setContentView(sheetBinding.root)
        dialog.show()
    }

    private fun setupMindMapCanvas() {
        binding.mindMapZoomPan.onTransformChanged = {
            updateBottomBarSelectionTitle()
        }
        binding.mindMapBoard.setNodeDraggingEnabled(false)
        binding.mindMapBoard.setListener(object : com.example.mymind.ui.mindmap.canvas.MindMapBoardView.Listener {
            override fun onNodeSelected(node: MindNodeEntity) {
                selectedNodeId = node.id
                showBottomBarFor(node)
                updateBottomBarSelectionTitle()
            }

            override fun onNodeDoubleTap(node: MindNodeEntity) {
                selectedNodeId = node.id
                showBottomBarFor(node)
                showEditNodeDialog(node)
            }

            override fun onNodeLongPress(node: MindNodeEntity) {
                selectedNodeId = node.id
                showBottomBarFor(node)
                showNodeContextMenu(node)
            }

            override fun onNodeMoved(nodeId: Long, fromX: Float?, fromY: Float?, toX: Float?, toY: Float?) {
                viewModel.updateNodePosition(nodeId, fromX, fromY, toX, toY)
            }

            override fun onNoteJumpClick(node: MindNodeEntity) {
                if (node.noteId != null) {
                    startActivity(NoteEditorActivity.createIntent(this@MindMapDetailActivity, node.noteId))
                } else {
                    showBindNoteDialog(node)
                }
            }

            override fun onBlankTap() {
                selectedNodeId = null
                binding.quickAddChildFab.visibility = android.view.View.GONE
                binding.mindMapBottomBar.visibility = android.view.View.GONE
                setLassoEnabled(false)
            }
        })
    }

    private fun setupActions() {
        binding.addNodeFab.setOnClickListener {
            showAddChildNodeDialog(selectedNodeId)
        }

        binding.quickAddChildFab.setOnClickListener {
            showAddChildNodeDialog(selectedNodeId)
        }

        binding.bottomAddChild.setOnClickListener {
            showAddChildNodeDialog(selectedNodeId)
        }
        binding.bottomUndo.setOnClickListener { viewModel.undo() }
        binding.bottomNote.setOnClickListener {
            val node = getSelectedNode() ?: return@setOnClickListener
            if (node.noteId != null) {
                startActivity(NoteEditorActivity.createIntent(this, node.noteId))
            } else {
                showBindNoteDialog(node)
            }
        }
        binding.bottomEdit.setOnClickListener {
            val node = getSelectedNode() ?: return@setOnClickListener
            showEditNodeDialog(node)
        }

    }

    private fun observeData() {
        viewModel.mindMapWithNodes.observe(this) { data: MindMapWithNodes? ->
            if (data == null) return@observe
            val activeNodes = data.nodes.filter { !it.isDeleted }

            binding.topAppBar.title = data.mindMap.title

            nodeAdapter.submitList(activeNodes)
            latestNodes = activeNodes
            binding.mindMapBoard.submit(activeNodes, notePreviewById)
            binding.mindMapBoard.setSelectedNode(selectedNodeId)
            val selected = selectedNodeId?.let { id -> activeNodes.firstOrNull { it.id == id } }
            if (selected != null) showBottomBarFor(selected) else binding.mindMapBottomBar.visibility = android.view.View.GONE
            updateBottomBarSelectionTitle()

            val pending = focusNodeId
            if (pending != null) {
                focusNode(pending)
                focusNodeId = null
                hasFitOnce = true
            } else if (!hasFitOnce) {
                binding.root.post {
                    binding.mindMapZoomPan.fitToScreen(animated = false)
                    hasFitOnce = true
                }
            }
        }

        viewModel.canUndo.observe(this) { canUndo ->
            binding.bottomUndo.isEnabled = canUndo
            binding.bottomUndo.alpha = if (canUndo) 1f else 0.35f
            binding.topAppBar.menu.findItem(R.id.action_undo)?.isEnabled = canUndo
        }
    }

    private fun showBottomBarFor(node: MindNodeEntity) {
        binding.bottomSelectedTitle.text = node.content.ifBlank { "已选中" }
        binding.bottomNote.alpha = if (node.noteId != null) 1f else 0.85f
        binding.mindMapBottomBar.visibility = android.view.View.VISIBLE
    }

    private fun updateBottomBarSelectionTitle() {
        val node = getSelectedNode()
        if (node == null) {
            binding.mindMapBottomBar.visibility = android.view.View.GONE
            return
        }
        binding.bottomSelectedTitle.text = node.content.ifBlank { "已选中" }
    }

    private fun getSelectedNode(): MindNodeEntity? {
        val id = selectedNodeId ?: return null
        return latestNodes.firstOrNull { it.id == id }
    }

    private fun focusNode(nodeId: Long) {
        val center = binding.mindMapBoard.getNodeCenter(nodeId) ?: return
        binding.mindMapZoomPan.centerOn(center.first, center.second)
    }

    private fun shareMindMap() {
        val title = binding.topAppBar.title?.toString().orEmpty().trim().ifBlank { "思维导图" }
        val nodeTitles = latestNodes
            .sortedWith(compareBy<MindNodeEntity> { it.depth }.thenBy { it.branchOrder }.thenBy { it.id })
            .take(30)
            .joinToString("\n") { node ->
                val prefix = if (node.isRoot) "" else "• ".repeat(node.depth.coerceAtMost(4))
                prefix + node.content
            }
        val text = listOf(title, nodeTitles).filter { it.isNotBlank() }.joinToString("\n\n")
        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(android.content.Intent.EXTRA_TEXT, text)
        }
        startActivity(android.content.Intent.createChooser(intent, "分享导图"))
    }

    private fun observeEvents() {
        // 打开笔记编辑器
        viewModel.openNoteEvent.observe(this) { noteId ->
            if (noteId == null) return@observe
            startActivity(NoteEditorActivity.createIntent(this, noteId))
            viewModel.consumeOpenNoteEvent()
        }

        viewModel.nodeAddedEvent.observe(this) { nodeId ->
            if (nodeId == null || nodeId <= 0) return@observe
            selectedNodeId = nodeId
            binding.mindMapBoard.setSelectedNode(nodeId)
            binding.root.post { focusNode(nodeId) }
            val node = latestNodes.firstOrNull { it.id == nodeId }
            if (node != null) {
                showBottomBarFor(node)
                updateBottomBarSelectionTitle()
                updateQuickAddChildFab()
            }
            viewModel.consumeNodeAddedEvent()
        }
    }

    /** 点击无绑定笔记的节点：选择新建或已有 */
    private fun showBindNoteDialog(node: MindNodeEntity) {
        val options = arrayOf("新建笔记", "选择已有笔记")
        MaterialAlertDialogBuilder(this)
            .setTitle("绑定笔记到「${node.content}」")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> viewModel.createNoteAndBind(node.id, mindMapId)
                    1 -> showSelectExistingNoteDialog(node)
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    /** 选择已有笔记 */
    private fun showSelectExistingNoteDialog(node: MindNodeEntity) {
        val notes = allNotes
        if (notes.isEmpty()) {
            Toast.makeText(this, "暂无笔记，请先新建笔记", Toast.LENGTH_SHORT).show()
            return
        }
        val titles = notes.map { it.title }.toTypedArray()
        MaterialAlertDialogBuilder(this)
            .setTitle("选择笔记绑定到「${node.content}」")
            .setItems(titles) { _, which ->
                viewModel.bindNoteToNode(node.id, notes[which].id)
                Toast.makeText(this, "已绑定笔记：${notes[which].title}", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    /** 长按节点：操作菜单 */
    private fun showNodeContextMenu(node: MindNodeEntity) {
        val items = mutableListOf<String>()
        items.add("重命名")
        items.add("添加子节点")
        items.add("样式修改")
        if (hasChildren(node.id)) {
            items.add(if (node.isCollapsed) "展开" else "折叠")
        }
        if (!node.isRoot) items.add("删除节点")
        items.add(if (node.noteId != null) "跳转笔记" else "绑定笔记")

        MaterialAlertDialogBuilder(this)
            .setTitle("节点操作：${node.content}")
            .setItems(items.toTypedArray()) { _, which ->
                handleNodeMenuAction(items[which], node)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun handleNodeMenuAction(action: String, node: MindNodeEntity) {
        when (action) {
            "重命名" -> showEditNodeDialog(node)
            "添加子节点" -> { showAddChildNodeDialog(node.id) }
            "样式修改" -> showStyleEntryDialog(node)
            "折叠", "展开" -> viewModel.toggleCollapse(node.id)
            "绑定笔记" -> showBindNoteDialog(node)
            "跳转笔记" -> node.noteId?.let { startActivity(NoteEditorActivity.createIntent(this, it)) }
            "删除节点" -> {
                MaterialAlertDialogBuilder(this)
                    .setTitle("确认删除")
                    .setMessage("删除节点「${node.content}」及其所有子节点？")
                    .setPositiveButton("删除") { _, _ -> viewModel.deleteSubtree(node.id) }
                    .setNegativeButton("取消", null)
                    .show()
            }
        }
    }

    private fun hasChildren(nodeId: Long): Boolean {
        return latestNodes.any { it.parentNodeId == nodeId && !it.isDeleted }
    }

    private fun setLassoEnabled(enabled: Boolean) {
        isLassoEnabled = enabled
        binding.mindMapBoard.setLassoModeEnabled(enabled)
    }

    private fun showMoreMenu() {
        val anchor: View = binding.topAppBar
        val popup = PopupMenu(this, anchor, Gravity.END)
        val redoId = 1
        val nodeListId = 2
        val trashId = 3
        val fitId = 4
        val fixWindowId = 5
        popup.menu.add(0, redoId, 0, "重做")
        popup.menu.add(0, nodeListId, 1, "节点列表")
        popup.menu.add(0, trashId, 2, "移到回收站")
        popup.menu.add(0, fitId, 3, "适配屏幕")
        popup.menu.add(0, fixWindowId, 4, "固定窗口边界")
        popup.menu.findItem(redoId).isEnabled = viewModel.canRedo.value == true
        popup.menu.findItem(fixWindowId).isCheckable = true
        popup.menu.findItem(fixWindowId).isChecked = true
        popup.setOnMenuItemClickListener { mi ->
            when (mi.itemId) {
                redoId -> {
                    viewModel.redo()
                    true
                }
                nodeListId -> {
                    showNodeListSheet()
                    true
                }
                trashId -> {
                    viewModel.trashMindMap(mindMapId)
                    startActivity(
                        Intent(this, MainActivity::class.java)
                            .putExtra(MainActivity.EXTRA_DESTINATION, MainActivity.DEST_MINDMAPS)
                            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    )
                    finish()
                    true
                }
                fitId -> {
                    binding.mindMapZoomPan.fitToScreen(animated = true)
                    true
                }
                fixWindowId -> {
                    mi.isChecked = !mi.isChecked
                    binding.mindMapZoomPan.setWindowFixEnabled(mi.isChecked)
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    private fun showStyleEntryDialog(node: MindNodeEntity) {
        val options = arrayOf("颜色", "字号", "恢复自动")
        MaterialAlertDialogBuilder(this)
            .setTitle("样式：${node.content}")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showColorStyleDialog(node)
                    1 -> showTextSizeDialog(node)
                    2 -> viewModel.applyStylePreset(node.id, null, null, null)
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showColorStyleDialog(node: MindNodeEntity) {
        val names = arrayOf("红", "蓝", "绿", "橙", "紫", "青", "灰")
        val colors = intArrayOf(
            0xFFE57373.toInt(),
            0xFF64B5F6.toInt(),
            0xFF81C784.toInt(),
            0xFFFFB74D.toInt(),
            0xFFBA68C8.toInt(),
            0xFF4DB6AC.toInt(),
            0xFF90A4AE.toInt()
        )
        MaterialAlertDialogBuilder(this)
            .setTitle("节点颜色")
            .setItems(names) { _, which ->
                val bg = colors[which]
                val tc = resolveTextColor(bg)
                viewModel.applyStylePreset(node.id, bg, tc, node.textSizeSp)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showTextSizeDialog(node: MindNodeEntity) {
        val options = arrayOf("小", "中", "大")
        MaterialAlertDialogBuilder(this)
            .setTitle("字号")
            .setItems(options) { _, which ->
                val size = when (which) {
                    0 -> if (node.isRoot) 16f else 12f
                    1 -> if (node.isRoot) 18f else 14f
                    else -> if (node.isRoot) 22f else 16f
                }
                viewModel.applyStylePreset(node.id, node.backgroundColor, node.textColor, size)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun resolveTextColor(backgroundColor: Int): Int {
        val r = (backgroundColor shr 16) and 0xFF
        val g = (backgroundColor shr 8) and 0xFF
        val b = backgroundColor and 0xFF
        val luminance = (0.2126f * r + 0.7152f * g + 0.0722f * b) / 255f
        return if (luminance < 0.55f) 0xFFFFFFFF.toInt() else 0xFF1F2937.toInt()
    }

    private fun showAddChildNodeDialog(parentId: Long?) {
        val editText = EditText(this).apply {
            setText("")
            setSingleLine(false)
            setHorizontallyScrolling(false)
            minLines = 3
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            requestFocus()
        }
        MaterialAlertDialogBuilder(this)
            .setTitle("新增子节点")
            .setView(editText)
            .setPositiveButton("确定") { _, _ ->
                val newContent = editText.text.toString().trim()
                if (newContent.isNotBlank()) {
                    viewModel.addChildNode(parentId, newContent)
                } else {
                    viewModel.addChildNode(parentId, "新节点")
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showEditNodeDialog(node: MindNodeEntity) {
        val editText = EditText(this).apply {
            setText(node.content)
            setSelection(text.length)
            setSingleLine(false)
            setHorizontallyScrolling(false)
            minLines = 3
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            requestFocus()
        }
        MaterialAlertDialogBuilder(this)
            .setTitle("编辑节点内容")
            .setView(editText)
            .setPositiveButton("保存") { _, _ ->
                val newContent = editText.text.toString().trim()
                if (newContent.isNotBlank()) {
                    viewModel.renameNode(node.id, newContent)
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun updateQuickAddChildFab() {
        val nodeId = selectedNodeId
        if (nodeId == null) {
            binding.quickAddChildFab.visibility = android.view.View.GONE
            return
        }
        val nodeView = binding.mindMapBoard.getNodeView(nodeId)
        if (nodeView == null) {
            binding.quickAddChildFab.visibility = android.view.View.GONE
            return
        }
        binding.quickAddChildFab.visibility = android.view.View.VISIBLE
        if (binding.quickAddChildFab.width == 0 || binding.quickAddChildFab.height == 0) {
            binding.quickAddChildFab.post { updateQuickAddChildFab() }
            return
        }
        run {
            val rootLoc = IntArray(2)
            val nodeLoc = IntArray(2)
            binding.root.getLocationOnScreen(rootLoc)
            nodeView.getLocationOnScreen(nodeLoc)
            val x = (nodeLoc[0] - rootLoc[0] + nodeView.width - binding.quickAddChildFab.width * 0.3f)
            val y = (nodeLoc[1] - rootLoc[1] - binding.quickAddChildFab.height * 0.3f)
            binding.quickAddChildFab.x = x
            binding.quickAddChildFab.y = y
        }
    }

    companion object {
        private const val EXTRA_MIND_MAP_ID = "extra_mind_map_id"
        private const val EXTRA_FOCUS_NODE_ID = "extra_focus_node_id"
        private const val INVALID_MIND_MAP_ID = -1L
        private const val INVALID_NODE_ID = -1L

        fun createIntent(context: Context, mindMapId: Long, focusNodeId: Long? = null): Intent {
            return Intent(context, MindMapDetailActivity::class.java).apply {
                putExtra(EXTRA_MIND_MAP_ID, mindMapId)
                if (focusNodeId != null) {
                    putExtra(EXTRA_FOCUS_NODE_ID, focusNodeId)
                }
            }
        }
    }
}
