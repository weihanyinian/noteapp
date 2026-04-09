package com.example.mymind.ui.note

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.mymind.MainActivity
import com.example.mymind.R
import com.example.mymind.databinding.ActivityNoteEditorBinding
import com.example.mymind.ui.note.handwriting.InkCanvasView
import com.example.mymind.util.UiFormatters
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min

/**
 * 笔记编辑页：
 * - 文字层：RichEditor（WebView 富文本）
 * - 手写层：InkCanvasView（笔/橡皮/套索/撤销重做/导入标注）
 * - 视图容器：ZoomPanLayout（缩放/平移）
 *
 * 交互策略：
 * - 移动端：单指优先写字，不把单指拖动当成“移动画布”
 * - 双指：保留平移/缩放用于浏览大画布
 * - 电脑测试：鼠标滚轮缩放、右键/中键拖拽平移
 */
class NoteEditorActivity : AppCompatActivity() {

    private lateinit var binding: ActivityNoteEditorBinding
    private val viewModel: NoteEditorViewModel by viewModels()

    private var noteId: Long? = null
    private var hasInitialContent = false
    private var isReadyForAutoSave = false
    private var isApplyingInitial = false
    private var autoSaveJob: Job? = null
    private var isHandwritingVisible: Boolean = false
    private var currentTitle: String = "未命名笔记"
    private var lastUpdatedAt: Long = System.currentTimeMillis()

    private var attachmentUri: String? = null
    private var attachmentMime: String? = null
    private var attachmentPageIndex: Int = 0

    private var pdfPfd: ParcelFileDescriptor? = null
    private var pdfRenderer: PdfRenderer? = null
    private var openedPdfUri: String? = null

    private val documentPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@registerForActivityResult
        contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION
        )
        onDocumentPicked(uri.toString(), contentResolver.getType(uri))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNoteEditorBinding.inflate(layoutInflater)
        setContentView(binding.root)

        noteId = intent.getLongExtra(EXTRA_NOTE_ID, INVALID_NOTE_ID)
            .takeIf { it != INVALID_NOTE_ID }

        setupToolbar()
        setupEditor()
        bindExistingNote()

        toggleHandwriting(show = true)
        binding.titleText.text = currentTitle
        updateMeta()
        binding.titleText.setOnClickListener { showRenameDialog() }

        binding.root.post {
            isReadyForAutoSave = true
            if (noteId == null) {
                hasInitialContent = true
            }
        }
    }

    override fun onPause() {
        super.onPause()
        flushAutoSave()
    }

    override fun onDestroy() {
        super.onDestroy()
        closePdf()
    }

    private fun setupToolbar() {
        binding.topAppBar.setNavigationOnClickListener {
            startActivity(
                Intent(this, MainActivity::class.java)
                    .putExtra(MainActivity.EXTRA_DESTINATION, MainActivity.DEST_NOTES)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            )
            finish()
        }
        binding.topAppBar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_share -> {
                    shareNote()
                    true
                }
                else -> false
            }
        }
    }

    private fun setupEditor() {
        binding.richEditor.apply {
            setEditorHeight(240)
            setPadding(16, 16, 16, 16)
            setPlaceholder(getString(R.string.note_editor_placeholder))
        }

        // 笔记手写层：单指用于写字（避免误触拖动画布），双指用于平移/缩放浏览。
        binding.inkZoomPan.setOneFingerPanEnabled(false)
        binding.inkZoomPan.setTwoFingerPanEnabled(true)

        binding.richEditor.setOnTextChangeListener {
            scheduleAutoSave()
        }

        binding.inkView.onChanged = {
            scheduleAutoSave()
        }

        binding.bottomPenFountain.setOnClickListener {
            binding.inkView.setBrush(InkCanvasView.Brush.FOUNTAIN)
            binding.inkView.setTool(InkCanvasView.Tool.PEN)
            toggleHandwriting(show = true)
            showSelectedPenLabel(InkCanvasView.Brush.FOUNTAIN)
        }
        binding.bottomPenMarker.setOnClickListener {
            binding.inkView.setBrush(InkCanvasView.Brush.MARKER)
            binding.inkView.setTool(InkCanvasView.Tool.PEN)
            toggleHandwriting(show = true)
            showSelectedPenLabel(InkCanvasView.Brush.MARKER)
        }
        binding.bottomPenPencil.setOnClickListener {
            binding.inkView.setBrush(InkCanvasView.Brush.PENCIL)
            binding.inkView.setTool(InkCanvasView.Tool.PEN)
            toggleHandwriting(show = true)
            showSelectedPenLabel(InkCanvasView.Brush.PENCIL)
        }
        binding.bottomSelect.setOnClickListener {
            binding.inkView.setTool(InkCanvasView.Tool.LASSO)
            toggleHandwriting(show = true)
        }

        binding.toolEraserPoint.setOnClickListener {
            binding.inkView.setTool(InkCanvasView.Tool.ERASER_POINT)
            toggleHandwriting(show = true)
        }
        binding.toolEraserArea.setOnClickListener {
            binding.inkView.setTool(InkCanvasView.Tool.ERASER_AREA)
            toggleHandwriting(show = true)
        }
        binding.toolUndo.setOnClickListener { binding.inkView.undo() }
        binding.toolRedo.setOnClickListener { binding.inkView.redo() }
        binding.toolWidth.setOnClickListener { showWidthDialog() }
        binding.toolDeleteSelection.setOnClickListener { binding.inkView.deleteSelection() }
        binding.toolImport.setOnClickListener { showImportDialog() }
        binding.toolToggleMode.setOnClickListener { toggleHandwriting(show = !isHandwritingVisible) }

        binding.colorBlack.setOnClickListener { applyInkColor(0xFF111827.toInt()) }
        binding.colorBlue.setOnClickListener { applyInkColor(0xFF1565C0.toInt()) }
        binding.colorRed.setOnClickListener { applyInkColor(0xFFD32F2F.toInt()) }
    }

    private fun showSelectedPenLabel(brush: InkCanvasView.Brush) {
        binding.labelPenFountain.visibility = if (brush == InkCanvasView.Brush.FOUNTAIN) android.view.View.VISIBLE else android.view.View.GONE
        binding.labelPenMarker.visibility = if (brush == InkCanvasView.Brush.MARKER) android.view.View.VISIBLE else android.view.View.GONE
        binding.labelPenPencil.visibility = if (brush == InkCanvasView.Brush.PENCIL) android.view.View.VISIBLE else android.view.View.GONE
    }

    private fun bindExistingNote() {
        val currentNoteId = noteId ?: return
        viewModel.observeNote(currentNoteId).observe(this) { note ->
            if (note == null || hasInitialContent) return@observe
            isApplyingInitial = true
            currentTitle = note.title
            lastUpdatedAt = note.updatedAt
            binding.titleText.text = currentTitle
            updateMeta()
            binding.richEditor.html = note.content
            binding.inkView.importJson(note.inkJson)
            attachmentUri = note.attachmentUri
            attachmentMime = note.attachmentMime
            attachmentPageIndex = note.attachmentPageIndex
            if (!attachmentUri.isNullOrBlank()) {
                toggleHandwriting(show = true)
                if (attachmentMime == "application/pdf") {
                    loadPdfPage(attachmentPageIndex)
                } else if (attachmentMime?.startsWith("image/") == true) {
                    loadImage()
                }
            }
            hasInitialContent = true
            isApplyingInitial = false
        }
    }

    private fun scheduleAutoSave() {
        if (!isReadyForAutoSave || !hasInitialContent || isApplyingInitial) return

        autoSaveJob?.cancel()
        autoSaveJob = lifecycleScope.launch {
            delay(2500)
            upsertAndKeepEditing(showToast = false)
        }
    }

    private fun flushAutoSave() {
        if (!isReadyForAutoSave || !hasInitialContent) return
        autoSaveJob?.cancel()
        autoSaveJob = null
        lifecycleScope.launch {
            upsertAndKeepEditing(showToast = false)
        }
    }

    private suspend fun upsertAndKeepEditing(showToast: Boolean) {
        val title = currentTitle.trim()
        val content = binding.richEditor.html.orEmpty()
        val inkJson = binding.inkView.exportJson().takeIf { it.contains("strokes") }
        noteId = viewModel.upsert(
            noteId = noteId,
            title = title,
            content = content,
            inkJson = inkJson,
            attachmentUri = attachmentUri,
            attachmentMime = attachmentMime,
            attachmentPageIndex = attachmentPageIndex
        )
        lastUpdatedAt = System.currentTimeMillis()
        updateMeta()
        if (showToast) {
            Toast.makeText(this, R.string.note_saved, Toast.LENGTH_SHORT).show()
        }
    }

    private fun showRenameDialog() {
        val editText = android.widget.EditText(this).apply {
            setText(currentTitle)
            setSelection(text.length)
        }
        MaterialAlertDialogBuilder(this)
            .setTitle("重命名")
            .setView(editText)
            .setPositiveButton("确定") { _, _ ->
                val newTitle = editText.text?.toString().orEmpty().trim().ifBlank { "未命名笔记" }
                currentTitle = newTitle
                binding.titleText.text = currentTitle
                scheduleAutoSave()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun updateMeta() {
        binding.metaText.text = "${UiFormatters.formatTime(lastUpdatedAt)} · 默认笔记本"
    }

    private fun showMoreActions() {
        val isPdf = attachmentMime == "application/pdf" && pdfRenderer != null
        val items = mutableListOf(
            "移到回收站"
        )
        if (isPdf) {
            items.add("上一页")
            items.add("下一页")
        }
        MaterialAlertDialogBuilder(this)
            .setTitle("更多")
            .setItems(items.toTypedArray()) { _, which ->
                when (items[which]) {
                    "上一页" -> changePdfPageBy(-1)
                    "下一页" -> changePdfPageBy(1)
                    "移到回收站" -> moveToTrash()
                    else -> return@setItems
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun applyInkColor(color: Int) {
        binding.inkView.setPenColor(color)
        binding.inkView.setTool(InkCanvasView.Tool.PEN)
        toggleHandwriting(show = true)
        scheduleAutoSave()
    }

    private fun showImportDialog() {
        val items = arrayOf("导入 PDF", "导入 PPT", "导入图片")
        MaterialAlertDialogBuilder(this)
            .setTitle("导入")
            .setItems(items) { _, which ->
                when (which) {
                    0 -> pickDocument(arrayOf("application/pdf"))
                    1 -> pickDocument(
                        arrayOf(
                            "application/vnd.ms-powerpoint",
                            "application/vnd.openxmlformats-officedocument.presentationml.presentation"
                        )
                    )
                    else -> pickDocument(arrayOf("image/*"))
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun pickDocument(mimeTypes: Array<String>) {
        documentPicker.launch(mimeTypes)
    }

    private fun onDocumentPicked(uriString: String, mime: String?) {
        attachmentUri = uriString
        attachmentMime = mime
        attachmentPageIndex = 0
        toggleHandwriting(show = true)

        if (mime == "application/pdf") {
            loadPdfPage(0)
        } else if (mime?.startsWith("image/") == true) {
            loadImage()
        } else {
            binding.attachmentImage.setImageDrawable(null)
            binding.attachmentImage.visibility = android.view.View.GONE
            Toast.makeText(this, "已导入文件（当前仅支持 PDF 直接渲染标注）", Toast.LENGTH_SHORT).show()
        }
        scheduleAutoSave()
    }

    private fun loadImage() {
        val uri = attachmentUri ?: return
        val parsed = android.net.Uri.parse(uri)
        runCatching {
            val stream = contentResolver.openInputStream(parsed) ?: return
            stream.use {
                val bitmap = android.graphics.BitmapFactory.decodeStream(it)
                binding.attachmentImage.setImageBitmap(bitmap)
                binding.attachmentImage.visibility = android.view.View.VISIBLE
            }
        }.onFailure {
            binding.attachmentImage.visibility = android.view.View.GONE
            Toast.makeText(this, "图片加载失败", Toast.LENGTH_SHORT).show()
        }
    }

    private fun toggleHandwriting(show: Boolean? = null) {
        val target = show ?: !isHandwritingVisible
        isHandwritingVisible = target
        binding.handwritingContainer.visibility = if (target) android.view.View.VISIBLE else android.view.View.GONE
        binding.handwritingToolbar.visibility = android.view.View.VISIBLE
        binding.richEditor.visibility = if (target) android.view.View.GONE else android.view.View.VISIBLE
    }

    private fun showColorDialog() {
        val items = arrayOf("蓝", "黑", "红", "绿", "紫")
        val colors = intArrayOf(
            0xFF1565C0.toInt(),
            0xFF212121.toInt(),
            0xFFD32F2F.toInt(),
            0xFF2E7D32.toInt(),
            0xFF6A1B9A.toInt()
        )
        MaterialAlertDialogBuilder(this)
            .setTitle("画笔颜色")
            .setItems(items) { _, which ->
                binding.inkView.setPenColor(colors.getOrNull(which) ?: colors[0])
                binding.inkView.setTool(InkCanvasView.Tool.PEN)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showWidthDialog() {
        val items = arrayOf("细", "中", "粗", "超粗")
        val widths = floatArrayOf(2.5f, 4.5f, 7.5f, 11f)
        MaterialAlertDialogBuilder(this)
            .setTitle("画笔粗细")
            .setItems(items) { _, which ->
                binding.inkView.setPenWidthDp(widths.getOrNull(which) ?: widths[1])
                binding.inkView.setTool(InkCanvasView.Tool.PEN)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun loadPdfPage(pageIndex: Int) {
        val uri = attachmentUri ?: return
        val parsed = android.net.Uri.parse(uri)
        runCatching {
            openPdfIfNeeded(parsed)
            val renderer = pdfRenderer ?: return
            val safeIndex = pageIndex.coerceIn(0, renderer.pageCount - 1)
            attachmentPageIndex = safeIndex
            val page = renderer.openPage(safeIndex)
            val targetWidth = min(1800, max(800, binding.root.width)).toInt()
            val scale = targetWidth.toFloat() / page.width.toFloat()
            val targetHeight = (page.height * scale).toInt()
            val bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            page.close()

            binding.attachmentImage.setImageBitmap(bitmap)
            binding.attachmentImage.visibility = android.view.View.VISIBLE
        }.onFailure {
            Toast.makeText(this, "PDF 加载失败", Toast.LENGTH_SHORT).show()
            binding.attachmentImage.visibility = android.view.View.GONE
        }
    }

    private fun openPdfIfNeeded(uri: android.net.Uri) {
        if (openedPdfUri == uri.toString() && pdfRenderer != null) return
        closePdf()
        val pfd = contentResolver.openFileDescriptor(uri, "r") ?: return
        pdfPfd = pfd
        pdfRenderer = PdfRenderer(pfd)
        openedPdfUri = uri.toString()
    }

    private fun closePdf() {
        runCatching { pdfRenderer?.close() }
        runCatching { pdfPfd?.close() }
        pdfRenderer = null
        pdfPfd = null
        openedPdfUri = null
    }

    private fun changePdfPageBy(delta: Int) {
        val renderer = pdfRenderer ?: return
        val next = (attachmentPageIndex + delta).coerceIn(0, renderer.pageCount - 1)
        if (next == attachmentPageIndex) return
        loadPdfPage(next)
        scheduleAutoSave()
    }

    private fun moveToTrash() {
        val currentId = noteId
        if (currentId == null) {
            finish()
            return
        }
        MaterialAlertDialogBuilder(this)
            .setTitle("移到回收站")
            .setMessage("确认将当前笔记移到回收站？")
            .setPositiveButton("确认") { _, _ ->
                lifecycleScope.launch {
                    viewModel.trash(currentId)
                    startActivity(
                        Intent(this@NoteEditorActivity, MainActivity::class.java)
                            .putExtra(MainActivity.EXTRA_DESTINATION, MainActivity.DEST_NOTES)
                            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    )
                    finish()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun shareNote() {
        val plain = UiFormatters.htmlToPlainText(binding.richEditor.html.orEmpty())
        val text = buildString {
            append(currentTitle)
            append("\n\n")
            if (plain.isNotBlank()) append(plain)
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        startActivity(Intent.createChooser(intent, "分享笔记"))
    }

    companion object {
        private const val EXTRA_NOTE_ID = "extra_note_id"
        private const val INVALID_NOTE_ID = -1L

        fun createIntent(context: Context, noteId: Long?): Intent {
            return Intent(context, NoteEditorActivity::class.java).apply {
                if (noteId != null) {
                    putExtra(EXTRA_NOTE_ID, noteId)
                }
            }
        }
    }
}
