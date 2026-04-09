package com.example.mymind.ui.home

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.ParcelFileDescriptor
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.mymind.data.local.entity.NoteEntity
import com.example.mymind.databinding.ItemRecentNoteBinding
import com.example.mymind.util.UiFormatters
import android.graphics.pdf.PdfRenderer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.max
import kotlin.math.min

class RecentNoteAdapter(
    private val onClick: (NoteEntity) -> Unit
) : ListAdapter<NoteEntity, RecentNoteAdapter.VH>(Diff) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemRecentNoteBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(getItem(position))
    }

    override fun onViewRecycled(holder: VH) {
        holder.cancelPending()
        super.onViewRecycled(holder)
    }

    inner class VH(
        private val binding: ItemRecentNoteBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        private var previewJob: Job? = null
        private var boundId: Long = -1L

        fun bind(item: NoteEntity) {
            boundId = item.id
            cancelPending()
            binding.titleText.text = item.title
            binding.previewText.text = buildPreviewText(item)
            binding.updatedAtText.text = UiFormatters.formatTime(item.updatedAt)
            binding.root.setOnClickListener { onClick(item) }

            binding.previewImage.setImageDrawable(null)
            binding.previewImage.visibility = android.view.View.GONE

            previewJob = scope.launch {
                val ctx = binding.root.context
                val targetWidth = ctx.resources.displayMetrics.density.let { (ctx.resources.displayMetrics.widthPixels * 0.70f).toInt().coerceIn((240f * it).toInt(), (900f * it).toInt()) }
                val targetHeight = (120f * ctx.resources.displayMetrics.density).toInt().coerceAtLeast(240)
                val bmp = withContext(Dispatchers.IO) {
                    buildPreviewBitmap(item, targetWidth, targetHeight, ctx.contentResolver)
                }
                if (bmp != null && boundId == item.id) {
                    binding.previewImage.setImageBitmap(bmp)
                    binding.previewImage.visibility = android.view.View.VISIBLE
                }
            }
        }

        fun cancelPending() {
            previewJob?.cancel()
            previewJob = null
        }
    }

    private fun buildPreviewText(item: NoteEntity): String {
        val fromText = UiFormatters.htmlToPlainText(item.content).trim()
        if (fromText.isNotBlank()) return fromText.take(80)
        if (!item.attachmentUri.isNullOrBlank()) return "附件：${item.attachmentMime.orEmpty()}"
        if (!item.inkJson.isNullOrBlank()) return "手写笔记"
        return ""
    }

    private fun buildPreviewBitmap(
        item: NoteEntity,
        targetWidth: Int,
        targetHeight: Int,
        contentResolver: android.content.ContentResolver
    ): Bitmap? {
        val uriString = item.attachmentUri
        val mime = item.attachmentMime
        if (!uriString.isNullOrBlank() && !mime.isNullOrBlank()) {
            val uri = android.net.Uri.parse(uriString)
            if (mime == "application/pdf") {
                return renderPdfPageThumbnail(uri, item.attachmentPageIndex, targetWidth, targetHeight, contentResolver)
            }
            if (mime.startsWith("image/")) {
                return renderImageThumbnail(uri, targetWidth, targetHeight, contentResolver)
            }
        }
        val inkJson = item.inkJson
        if (!inkJson.isNullOrBlank()) {
            return renderInkThumbnail(inkJson, targetWidth, targetHeight)
        }
        return null
    }

    private fun renderImageThumbnail(
        uri: android.net.Uri,
        targetWidth: Int,
        targetHeight: Int,
        contentResolver: android.content.ContentResolver
    ): Bitmap? {
        val stream = contentResolver.openInputStream(uri) ?: return null
        stream.use {
            val bitmap = BitmapFactory.decodeStream(it) ?: return null
            val scale = max(targetWidth.toFloat() / bitmap.width.toFloat(), targetHeight.toFloat() / bitmap.height.toFloat())
            val w = (bitmap.width * scale).toInt().coerceAtLeast(1)
            val h = (bitmap.height * scale).toInt().coerceAtLeast(1)
            val scaled = Bitmap.createScaledBitmap(bitmap, w, h, true)
            val out = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(out)
            val left = (targetWidth - scaled.width) * 0.5f
            val top = (targetHeight - scaled.height) * 0.5f
            canvas.drawBitmap(scaled, left, top, null)
            return out
        }
    }

    private fun renderPdfPageThumbnail(
        uri: android.net.Uri,
        pageIndex: Int,
        targetWidth: Int,
        targetHeight: Int,
        contentResolver: android.content.ContentResolver
    ): Bitmap? {
        val pfd: ParcelFileDescriptor = contentResolver.openFileDescriptor(uri, "r") ?: return null
        pfd.use { safePfd ->
            val renderer = PdfRenderer(safePfd)
            try {
                if (renderer.pageCount <= 0) return null
                val safeIndex = pageIndex.coerceIn(0, renderer.pageCount - 1)
                val page = renderer.openPage(safeIndex)
                try {
                    val scale = min(targetWidth.toFloat() / page.width.toFloat(), targetHeight.toFloat() / page.height.toFloat())
                    val w = (page.width * scale).toInt().coerceAtLeast(1)
                    val h = (page.height * scale).toInt().coerceAtLeast(1)
                    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    val out = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(out)
                    val left = (targetWidth - w) * 0.5f
                    val top = (targetHeight - h) * 0.5f
                    canvas.drawBitmap(bmp, left, top, null)
                    return out
                } finally {
                    runCatching { page.close() }
                }
            } finally {
                runCatching { renderer.close() }
            }
        }
    }

    private fun renderInkThumbnail(
        inkJson: String,
        targetWidth: Int,
        targetHeight: Int
    ): Bitmap? {
        val strokesArr = runCatching { JSONObject(inkJson).optJSONArray("strokes") ?: JSONArray() }.getOrNull() ?: return null
        if (strokesArr.length() == 0) return null

        var minX = Float.POSITIVE_INFINITY
        var minY = Float.POSITIVE_INFINITY
        var maxX = Float.NEGATIVE_INFINITY
        var maxY = Float.NEGATIVE_INFINITY

        for (i in 0 until strokesArr.length()) {
            val s = strokesArr.optJSONObject(i) ?: continue
            val ox = s.optDouble("offsetX", 0.0).toFloat()
            val oy = s.optDouble("offsetY", 0.0).toFloat()
            val pts = s.optJSONArray("points") ?: continue
            for (j in 0 until pts.length()) {
                val p = pts.optJSONObject(j) ?: continue
                val x = p.optDouble("x", 0.0).toFloat() + ox
                val y = p.optDouble("y", 0.0).toFloat() + oy
                minX = min(minX, x)
                minY = min(minY, y)
                maxX = max(maxX, x)
                maxY = max(maxY, y)
            }
        }

        if (!minX.isFinite() || !minY.isFinite() || !maxX.isFinite() || !maxY.isFinite()) return null

        val pad = 24f
        val contentW = (maxX - minX).coerceAtLeast(1f)
        val contentH = (maxY - minY).coerceAtLeast(1f)
        val scale = min((targetWidth - pad * 2f) / contentW, (targetHeight - pad * 2f) / contentH).coerceIn(0.05f, 4f)

        val out = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

        val dx = pad + (targetWidth - pad * 2f - contentW * scale) * 0.5f - minX * scale
        val dy = pad + (targetHeight - pad * 2f - contentH * scale) * 0.5f - minY * scale

        for (i in 0 until strokesArr.length()) {
            val s = strokesArr.optJSONObject(i) ?: continue
            val color = s.optInt("color", 0xFF1565C0.toInt())
            val baseWidth = s.optDouble("baseWidth", 6.0).toFloat()
            val ox = s.optDouble("offsetX", 0.0).toFloat()
            val oy = s.optDouble("offsetY", 0.0).toFloat()
            val pts = s.optJSONArray("points") ?: continue
            if (pts.length() == 0) continue

            paint.color = color
            paint.strokeWidth = max(1f, baseWidth * 0.55f * scale)

            var lastX = Float.NaN
            var lastY = Float.NaN
            for (j in 0 until pts.length()) {
                val p = pts.optJSONObject(j) ?: continue
                val x = (p.optDouble("x", 0.0).toFloat() + ox) * scale + dx
                val y = (p.optDouble("y", 0.0).toFloat() + oy) * scale + dy
                if (j == 0) {
                    lastX = x
                    lastY = y
                    continue
                }
                canvas.drawLine(lastX, lastY, x, y, paint)
                lastX = x
                lastY = y
            }
        }

        val rectPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1f
            color = 0x14000000
        }
        canvas.drawRoundRect(RectF(0f, 0f, targetWidth.toFloat(), targetHeight.toFloat()), 24f, 24f, rectPaint)

        return out
    }

    private object Diff : DiffUtil.ItemCallback<NoteEntity>() {
        override fun areItemsTheSame(oldItem: NoteEntity, newItem: NoteEntity): Boolean = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: NoteEntity, newItem: NoteEntity): Boolean = oldItem == newItem
    }
}
