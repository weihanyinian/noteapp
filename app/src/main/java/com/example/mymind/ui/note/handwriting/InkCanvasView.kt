package com.example.mymind.ui.note.handwriting

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import androidx.core.graphics.withSave
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * 轻量手写画布。
 *
 * 数据模型：
 * - Stroke：一条笔画，包含颜色、基准粗细与一系列带 pressure 的点
 * - 通过 offsetX/offsetY 支持“移动选中笔画”的能力
 *
 * 工具：
 * - PEN：绘制笔画（根据 pressure 做粗细变化）
 * - ERASER_POINT：点擦（按半径命中并删除笔画）
 * - ERASER_AREA：区域擦（用套索圈选命中笔画并删除）
 * - LASSO：套索选择（圈选命中笔画，显示选中虚线框并可整体移动）
 *
 * 序列化：
 * - exportJson/importJson 仅保存 strokes（不保存撤销栈）
 */
class InkCanvasView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    enum class InteractionMode {
        DRAW,
        PAN
    }

    enum class Tool {
        PEN,
        ERASER_POINT,
        ERASER_AREA,
        LASSO
    }

    enum class Brush {
        FOUNTAIN,
        MARKER,
        PENCIL,
        HIGHLIGHTER
    }

    private data class InkPoint(val x: Float, val y: Float, val pressure: Float)

    private data class Stroke(
        val id: Long,
        val brush: Brush,
        val color: Int,
        val baseWidth: Float,
        val points: MutableList<InkPoint>,
        var offsetX: Float = 0f,
        var offsetY: Float = 0f
    )

    private data class Action(
        val removed: List<Stroke>,
        val added: List<Stroke>
    )

    private var nextStrokeId: Long = 1
    private val strokes = ArrayList<Stroke>()
    private val history = ArrayDeque<Action>()
    private val undone = ArrayDeque<Action>()
    private val selectedIds = LinkedHashSet<Long>()

    private var tool: Tool = Tool.PEN
    private var brush: Brush = Brush.FOUNTAIN
    private var penColor: Int = 0xFF1565C0.toInt()
    private var penBaseWidthPx: Float = 6f * resources.displayMetrics.density
    private var penMinWidthPx: Float = 2f * resources.displayMetrics.density
    private var penMaxWidthPx: Float = 14f * resources.displayMetrics.density

    private var eraserRadiusPx: Float = 26f * resources.displayMetrics.density

    private val inkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val selectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f * resources.displayMetrics.density
        color = 0xFF1E88E5.toInt()
    }

    private val lassoPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f * resources.displayMetrics.density
        color = 0xAA424242.toInt()
    }

    private val lassoPath = Path()
    private val lassoPoints = ArrayList<Pair<Float, Float>>()
    private var isLassoDrawing = false

    private var currentStroke: Stroke? = null
    private var lastX = 0f
    private var lastY = 0f
    private var activePointerId: Int = MotionEvent.INVALID_POINTER_ID
    private var isMovingSelection = false
    private var selectionBounds: RectF? = null

    var onChanged: (() -> Unit)? = null

    private var interactionMode: InteractionMode = InteractionMode.DRAW
    private var scaleFactor: Float = 1f
    private var translationXInternal: Float = 0f
    private var translationYInternal: Float = 0f
    private val minScale: Float = 0.35f
    private val maxScale: Float = 3.0f
    private val pageSizePx: Float = 3000f * resources.displayMetrics.density
    private val pageCenterX: Float = pageSizePx * 0.5f
    private val pageCenterY: Float = pageSizePx * 0.5f

    private data class AttachmentPage(val bitmap: Bitmap, val rect: RectF)
    private val attachmentPages = ArrayList<AttachmentPage>()
    private val attachmentBounds = RectF()

    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDoubleTap(e: MotionEvent): Boolean {
            centerToPage()
            return true
        }
    })

    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
            cancelCurrentStroke()
            isMovingSelection = false
            isLassoDrawing = false
            lassoPath.reset()
            lassoPoints.clear()
            return true
        }

        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val newScale = (scaleFactor * detector.scaleFactor).coerceIn(minScale, maxScale)
            val focusX = detector.focusX
            val focusY = detector.focusY
            val scaleChange = newScale / scaleFactor
            translationXInternal = focusX - (focusX - translationXInternal) * scaleChange
            translationYInternal = focusY - (focusY - translationYInternal) * scaleChange
            scaleFactor = newScale
            postInvalidateOnAnimation()
            return true
        }
    })

    private var ruledEnabled: Boolean = true
    private val ruledLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f * resources.displayMetrics.density
        color = 0x33000000
    }
    private val marginLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * resources.displayMetrics.density
        color = 0x55000000
    }
    private val ruledSpacingPx: Float = 48f * resources.displayMetrics.density
    private val ruledTopPaddingPx: Float = 32f * resources.displayMetrics.density
    private val ruledLeftMarginPx: Float = 120f * resources.displayMetrics.density

    fun setRuledEnabled(enabled: Boolean) {
        ruledEnabled = enabled
        postInvalidateOnAnimation()
    }

    fun setInteractionMode(mode: InteractionMode) {
        interactionMode = mode
        if (mode == InteractionMode.DRAW) {
            activePointerId = MotionEvent.INVALID_POINTER_ID
            isMovingSelection = false
            isLassoDrawing = false
            lassoPath.reset()
            lassoPoints.clear()
        }
        postInvalidateOnAnimation()
    }

    fun setAttachmentBitmap(bitmap: Bitmap?) {
        setAttachmentBitmaps(bitmap?.let { listOf(it) })
    }

    fun setAttachmentBitmaps(bitmaps: List<Bitmap>?) {
        attachmentPages.clear()
        attachmentBounds.setEmpty()
        if (bitmaps.isNullOrEmpty()) {
            postInvalidateOnAnimation()
            return
        }

        val maxW = pageSizePx * 0.86f
        val gap = 24f * resources.displayMetrics.density
        var cursorTop = 0f
        bitmaps.forEachIndexed { index, bmp ->
            val bw = bmp.width.toFloat()
            val bh = bmp.height.toFloat()
            val scale = (maxW / bw).coerceAtMost(1f)
            val w = bw * scale
            val h = bh * scale
            val left = pageCenterX - w * 0.5f
            val top = if (index == 0) pageCenterY - h * 0.5f else cursorTop + gap
            val rect = RectF(left, top, left + w, top + h)
            attachmentPages.add(AttachmentPage(bitmap = bmp, rect = rect))
            cursorTop = rect.bottom
            if (attachmentBounds.isEmpty) {
                attachmentBounds.set(rect)
            } else {
                attachmentBounds.union(rect)
            }
        }
        postInvalidateOnAnimation()
    }

    fun appendAttachmentBitmap(bitmap: Bitmap) {
        val maxW = pageSizePx * 0.86f
        val gap = 24f * resources.displayMetrics.density
        val bw = bitmap.width.toFloat()
        val bh = bitmap.height.toFloat()
        val scale = (maxW / bw).coerceAtMost(1f)
        val w = bw * scale
        val h = bh * scale
        val left = pageCenterX - w * 0.5f
        val top = if (attachmentPages.isEmpty()) pageCenterY - h * 0.5f else attachmentPages.last().rect.bottom + gap
        val rect = RectF(left, top, left + w, top + h)
        attachmentPages.add(AttachmentPage(bitmap = bitmap, rect = rect))
        if (attachmentBounds.isEmpty) attachmentBounds.set(rect) else attachmentBounds.union(rect)
        postInvalidateOnAnimation()
    }

    fun centerToPage() {
        if (!attachmentBounds.isEmpty) {
            val cx = (attachmentBounds.left + attachmentBounds.right) * 0.5f
            val cy = (attachmentBounds.top + attachmentBounds.bottom) * 0.5f
            translationXInternal = width * 0.5f - cx * scaleFactor
            translationYInternal = height * 0.5f - cy * scaleFactor
        } else {
            translationXInternal = width * 0.5f - pageCenterX * scaleFactor
            translationYInternal = height * 0.5f - pageCenterY * scaleFactor
        }
        postInvalidateOnAnimation()
    }

    /** 切换当前工具（笔/橡皮/套索）。切到非套索时会清理套索绘制过程。 */
    fun setTool(tool: Tool) {
        this.tool = tool
        if (tool != Tool.LASSO) {
            isLassoDrawing = false
            lassoPath.reset()
            lassoPoints.clear()
        }
        invalidate()
    }

    /** 切换笔刷风格（影响渲染透明度与质感）。 */
    fun setBrush(brush: Brush) {
        this.brush = brush
    }

    /** 设置画笔颜色（ARGB）。 */
    fun setPenColor(color: Int) {
        penColor = color
    }

    /** 设置画笔基准粗细（dp）。pressure 会在 min/max 范围内动态映射。 */
    fun setPenWidthDp(widthDp: Float) {
        val px = widthDp * resources.displayMetrics.density
        penBaseWidthPx = px
        penMinWidthPx = max(1f, px * 0.35f)
        penMaxWidthPx = max(penMinWidthPx + 1f, px * 2.2f)
    }

    fun setEraserRadiusDp(radiusDp: Float) {
        val density = resources.displayMetrics.density
        eraserRadiusPx = (radiusDp * density).coerceIn(6f * density, 140f * density)
    }

    fun getEraserRadiusDp(): Float = eraserRadiusPx / resources.displayMetrics.density

    /** 撤销：移除最后一条笔画并放入 undone 栈。 */
    fun undo() {
        val action = history.removeLastOrNull() ?: return
        if (action.added.isNotEmpty()) {
            val ids = action.added.mapTo(HashSet()) { it.id }
            strokes.removeAll { ids.contains(it.id) }
            ids.forEach { selectedIds.remove(it) }
        }
        if (action.removed.isNotEmpty()) {
            strokes.addAll(action.removed)
        }
        recalcSelectionBounds()
        invalidate()
        onChanged?.invoke()
        undone.addLast(action)
    }

    /** 重做：从 undone 栈取出并恢复到 strokes。 */
    fun redo() {
        val action = undone.removeLastOrNull() ?: return
        if (action.removed.isNotEmpty()) {
            val ids = action.removed.mapTo(HashSet()) { it.id }
            strokes.removeAll { ids.contains(it.id) }
            ids.forEach { selectedIds.remove(it) }
        }
        if (action.added.isNotEmpty()) {
            strokes.addAll(action.added)
        }
        invalidate()
        onChanged?.invoke()
        history.addLast(action)
    }

    fun clearSelection() {
        if (selectedIds.isEmpty()) return
        selectedIds.clear()
        selectionBounds = null
        invalidate()
    }

    /** 删除当前选中的笔画。 */
    fun deleteSelection() {
        if (selectedIds.isEmpty()) return
        val ids = selectedIds.toSet()
        val removed = strokes.filter { ids.contains(it.id) }
        strokes.removeAll { ids.contains(it.id) }
        selectedIds.clear()
        selectionBounds = null
        pushAction(removed = removed, added = emptyList())
        invalidate()
        onChanged?.invoke()
    }

    fun clearAllStrokes() {
        if (strokes.isEmpty()) return
        val removed = strokes.toList()
        strokes.clear()
        selectedIds.clear()
        selectionBounds = null
        currentStroke = null
        isMovingSelection = false
        pushAction(removed = removed, added = emptyList())
        invalidate()
        onChanged?.invoke()
    }

    /** 将当前笔画序列化为 JSON 字符串（用于持久化）。 */
    fun exportJson(): String {
        val root = JSONObject()
        val arr = JSONArray()
        strokes.forEach { s ->
            val obj = JSONObject()
            obj.put("id", s.id)
            obj.put("brush", s.brush.name)
            obj.put("color", s.color)
            obj.put("baseWidth", s.baseWidth)
            obj.put("offsetX", s.offsetX)
            obj.put("offsetY", s.offsetY)
            val pts = JSONArray()
            s.points.forEach { p ->
                val pObj = JSONObject()
                pObj.put("x", p.x)
                pObj.put("y", p.y)
                pObj.put("pressure", p.pressure)
                pts.put(pObj)
            }
            obj.put("points", pts)
            arr.put(obj)
        }
        root.put("strokes", arr)
        return root.toString()
    }

    /** 从 JSON 恢复笔画（清空现有内容与撤销栈）。 */
    fun importJson(json: String?) {
        strokes.clear()
        undone.clear()
        history.clear()
        selectedIds.clear()
        selectionBounds = null
        nextStrokeId = 1
        if (json.isNullOrBlank()) {
            invalidate()
            return
        }
        runCatching {
            val root = JSONObject(json)
            val arr = root.optJSONArray("strokes") ?: JSONArray()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val id = obj.optLong("id")
                val brush = runCatching { Brush.valueOf(obj.optString("brush", Brush.FOUNTAIN.name)) }.getOrElse { Brush.FOUNTAIN }
                val color = obj.optInt("color", 0xFF1565C0.toInt())
                val baseWidth = obj.optDouble("baseWidth", (6f * resources.displayMetrics.density).toDouble()).toFloat()
                val offsetX = obj.optDouble("offsetX", 0.0).toFloat()
                val offsetY = obj.optDouble("offsetY", 0.0).toFloat()
                val ptsArr = obj.optJSONArray("points") ?: JSONArray()
                val pts = ArrayList<InkPoint>(ptsArr.length())
                for (j in 0 until ptsArr.length()) {
                    val pObj = ptsArr.getJSONObject(j)
                    pts.add(
                        InkPoint(
                            x = pObj.optDouble("x", 0.0).toFloat(),
                            y = pObj.optDouble("y", 0.0).toFloat(),
                            pressure = pObj.optDouble("pressure", 1.0).toFloat()
                        )
                    )
                }
                strokes.add(Stroke(id = id, brush = brush, color = color, baseWidth = baseWidth, points = pts, offsetX = offsetX, offsetY = offsetY))
                nextStrokeId = max(nextStrokeId, id + 1)
            }
        }
        invalidate()
    }

    private var isTwoFingerGesture = false
    private var lastFocusX = 0f
    private var lastFocusY = 0f

    override fun onTouchEvent(event: MotionEvent): Boolean {
        parent?.requestDisallowInterceptTouchEvent(true)

        gestureDetector.onTouchEvent(event)
        scaleDetector.onTouchEvent(event)

        val safeScale = max(0.001f, scaleFactor)

        fun focusX(): Float {
            var sum = 0f
            for (i in 0 until event.pointerCount) sum += event.getX(i)
            return sum / event.pointerCount.toFloat()
        }
        fun focusY(): Float {
            var sum = 0f
            for (i in 0 until event.pointerCount) sum += event.getY(i)
            return sum / event.pointerCount.toFloat()
        }

        if (interactionMode == InteractionMode.PAN) {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    activePointerId = event.getPointerId(0)
                    lastX = event.x
                    lastY = event.y
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (scaleDetector.isInProgress) return true
                    val idx = event.findPointerIndex(activePointerId)
                    if (idx < 0) return true
                    val x = event.getX(idx)
                    val y = event.getY(idx)
                    val dx = x - lastX
                    val dy = y - lastY
                    lastX = x
                    lastY = y
                    translationXInternal += dx
                    translationYInternal += dy
                    postInvalidateOnAnimation()
                    return true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    activePointerId = MotionEvent.INVALID_POINTER_ID
                    return true
                }
            }
            return true
        }

        fun toWorldX(x: Float): Float = (x - translationXInternal) / safeScale
        fun toWorldY(y: Float): Float = (y - translationYInternal) / safeScale

        if (event.pointerCount >= 2 || scaleDetector.isInProgress || isTwoFingerGesture) {
            when (event.actionMasked) {
                MotionEvent.ACTION_POINTER_DOWN -> {
                    cancelCurrentStroke()
                    isMovingSelection = false
                    isLassoDrawing = false
                    lassoPath.reset()
                    lassoPoints.clear()
                    isTwoFingerGesture = true
                    lastFocusX = focusX()
                    lastFocusY = focusY()
                    activePointerId = MotionEvent.INVALID_POINTER_ID
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (!isTwoFingerGesture) {
                        isTwoFingerGesture = true
                        lastFocusX = focusX()
                        lastFocusY = focusY()
                        return true
                    }
                    val fx = focusX()
                    val fy = focusY()
                    val dx = fx - lastFocusX
                    val dy = fy - lastFocusY
                    lastFocusX = fx
                    lastFocusY = fy
                    translationXInternal += dx
                    translationYInternal += dy
                    postInvalidateOnAnimation()
                    return true
                }
                MotionEvent.ACTION_POINTER_UP -> {
                    if (event.pointerCount - 1 < 2) {
                        isTwoFingerGesture = false
                    } else {
                        lastFocusX = focusX()
                        lastFocusY = focusY()
                    }
                    return true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    isTwoFingerGesture = false
                    return true
                }
            }
        }

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                activePointerId = event.getPointerId(0)
                val idx = event.findPointerIndex(activePointerId).coerceAtLeast(0)
                val toolType = event.getToolType(idx)
                val isStylus = toolType == MotionEvent.TOOL_TYPE_STYLUS || toolType == MotionEvent.TOOL_TYPE_ERASER
                val x = toWorldX(event.getX(idx))
                val y = toWorldY(event.getY(idx))
                val pressure = normalizePressure(event.getPressure(idx), isStylus)

                lastX = x
                lastY = y

                if (tool == Tool.LASSO && !isStylus) {
                    beginLasso(x, y)
                    return true
                }
                if (!isStylus && selectedIds.isNotEmpty() && selectionBounds?.contains(x, y) == true) {
                    isMovingSelection = true
                    return true
                }

                when (tool) {
                    Tool.PEN -> startStroke(x, y, pressure = pressure)
                    Tool.ERASER_POINT -> eraseAt(x, y)
                    Tool.ERASER_AREA -> beginLasso(x, y)
                    Tool.LASSO -> beginLasso(x, y)
                }
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (activePointerId == MotionEvent.INVALID_POINTER_ID) return true
                val idx = event.findPointerIndex(activePointerId)
                if (idx < 0) return true

                val toolType = event.getToolType(idx)
                val isStylus = toolType == MotionEvent.TOOL_TYPE_STYLUS || toolType == MotionEvent.TOOL_TYPE_ERASER
                val x = toWorldX(event.getX(idx))
                val y = toWorldY(event.getY(idx))
                val pressure = normalizePressure(event.getPressure(idx), isStylus)

                val dx = x - lastX
                val dy = y - lastY
                lastX = x
                lastY = y

                if (isMovingSelection) {
                    moveSelection(dx, dy)
                    return true
                }
                if (isLassoDrawing) {
                    updateLasso(x, y)
                    return true
                }

                when (tool) {
                    Tool.PEN -> appendStroke(x, y, pressure = pressure)
                    Tool.ERASER_POINT -> eraseAt(x, y)
                    Tool.ERASER_AREA -> updateLasso(x, y)
                    Tool.LASSO -> updateLasso(x, y)
                }
                return true
            }
            MotionEvent.ACTION_POINTER_UP -> {
                val pointerId = event.getPointerId(event.actionIndex)
                if (pointerId == activePointerId) {
                    val newIndex = if (event.pointerCount > 1) {
                        if (event.actionIndex == 0) 1 else 0
                    } else {
                        -1
                    }
                    if (newIndex >= 0) {
                        activePointerId = event.getPointerId(newIndex)
                        lastX = toWorldX(event.getX(newIndex))
                        lastY = toWorldY(event.getY(newIndex))
                    } else {
                        activePointerId = MotionEvent.INVALID_POINTER_ID
                    }
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                activePointerId = MotionEvent.INVALID_POINTER_ID
                if (isMovingSelection) {
                    isMovingSelection = false
                    onChanged?.invoke()
                    return true
                }
                if (isLassoDrawing) {
                    endLasso()
                    return true
                }
                if (tool == Tool.PEN) {
                    endStroke()
                    return true
                }
                return true
            }
        }

        return true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val safeScale = max(0.001f, scaleFactor)
        val worldLeft = (-translationXInternal) / safeScale
        val worldTop = (-translationYInternal) / safeScale
        val worldRight = (width - translationXInternal) / safeScale
        val worldBottom = (height - translationYInternal) / safeScale

        canvas.withSave {
            translate(translationXInternal, translationYInternal)
            scale(safeScale, safeScale)

            if (ruledEnabled) {
                val spacing = ruledSpacingPx
                var y = floor((worldTop - ruledTopPaddingPx) / spacing) * spacing + ruledTopPaddingPx
                while (y <= worldBottom) {
                    drawLine(worldLeft, y, worldRight, y, ruledLinePaint)
                    y += spacing
                }
                drawLine(ruledLeftMarginPx, worldTop, ruledLeftMarginPx, worldBottom, marginLinePaint)
            }

            if (attachmentPages.isNotEmpty()) {
                attachmentPages.forEach { page ->
                    if (page.rect.bottom < worldTop || page.rect.top > worldBottom) return@forEach
                    drawBitmap(page.bitmap, null, page.rect, null)
                }
            }

            strokes.forEach { s ->
                val sb = s.brush
                inkPaint.color = when (sb) {
                    Brush.FOUNTAIN -> s.color
                    Brush.MARKER -> (s.color and 0x00FFFFFF) or 0xAA000000.toInt()
                    Brush.PENCIL -> (s.color and 0x00FFFFFF) or 0x88000000.toInt()
                    Brush.HIGHLIGHTER -> (s.color and 0x00FFFFFF) or 0x66000000.toInt()
                }
                val pts = s.points
                if (pts.size == 1) {
                    val p = pts[0]
                    val w = widthAtPressure(sb, s.baseWidth, p.pressure)
                    inkPaint.strokeWidth = w
                    drawPoint(p.x + s.offsetX, p.y + s.offsetY, inkPaint)
                    return@forEach
                }
                if (sb == Brush.MARKER || sb == Brush.PENCIL || sb == Brush.HIGHLIGHTER) {
                    val path = Path()
                    path.moveTo(pts[0].x + s.offsetX, pts[0].y + s.offsetY)
                    for (i in 1 until pts.size) {
                        val p = pts[i]
                        path.lineTo(p.x + s.offsetX, p.y + s.offsetY)
                    }
                    val midPressure = (pts.first().pressure + pts.last().pressure) * 0.5f
                    inkPaint.strokeWidth = widthAtPressure(sb, s.baseWidth, midPressure)
                    drawPath(path, inkPaint)
                } else {
                    for (i in 1 until pts.size) {
                        val p0 = pts[i - 1]
                        val p1 = pts[i]
                        val w = widthAtPressure(sb, s.baseWidth, (p0.pressure + p1.pressure) * 0.5f)
                        inkPaint.strokeWidth = w
                        drawLine(p0.x + s.offsetX, p0.y + s.offsetY, p1.x + s.offsetX, p1.y + s.offsetY, inkPaint)
                    }
                }
            }

            if (selectedIds.isNotEmpty()) {
                selectionBounds?.let { rect ->
                    val r = 16f * resources.displayMetrics.density
                    drawRoundRect(rect, r, r, selectionPaint)
                }
            }

            if (isLassoDrawing && lassoPoints.size > 1) {
                drawPath(lassoPath, lassoPaint)
            }
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (translationXInternal == 0f && translationYInternal == 0f) {
            centerToPage()
        }
    }

    private fun startStroke(x: Float, y: Float, pressure: Float) {
        clearSelection()
        undone.clear()
        val stroke = Stroke(
            id = nextStrokeId++,
            brush = brush,
            color = penColor,
            baseWidth = penBaseWidthPx,
            points = mutableListOf(InkPoint(x, y, pressure))
        )
        currentStroke = stroke
        strokes.add(stroke)
        invalidate()
    }

    private fun appendStroke(x: Float, y: Float, pressure: Float) {
        val stroke = currentStroke ?: return
        val last = stroke.points.lastOrNull()
        if (last != null) {
            val dist = abs(x - last.x) + abs(y - last.y)
            if (dist < 1.2f) return
        }
        stroke.points.add(InkPoint(x, y, pressure))
        invalidate()
    }

    private fun endStroke() {
        val stroke = currentStroke
        currentStroke = null
        if (stroke != null) {
            pushAction(removed = emptyList(), added = listOf(stroke))
        }
        invalidate()
        onChanged?.invoke()
    }

    private fun eraseAt(x: Float, y: Float) {
        val r = eraserRadiusPx / max(0.001f, scaleFactor)
        val r2 = r * r
        val removedStrokes = ArrayList<Stroke>()
        val addedStrokes = ArrayList<Stroke>()

        val iter = strokes.listIterator()
        while (iter.hasNext()) {
            val s = iter.next()
            val pts = s.points
            if (pts.isEmpty()) continue

            if (pts.size == 1) {
                val p = pts[0]
                val px = p.x + s.offsetX
                val py = p.y + s.offsetY
                val dx = px - x
                val dy = py - y
                if (dx * dx + dy * dy <= r2) {
                    removedStrokes.add(s)
                    iter.remove()
                }
                continue
            }

            var touched = false
            val segments = ArrayList<MutableList<InkPoint>>()
            var current = ArrayList<InkPoint>()
            for (i in pts.indices) {
                val p = pts[i]
                val px = p.x + s.offsetX
                val py = p.y + s.offsetY
                var erased = false
                val dx = px - x
                val dy = py - y
                if (dx * dx + dy * dy <= r2) {
                    erased = true
                } else if (i > 0) {
                    val a = pts[i - 1]
                    val ax = a.x + s.offsetX
                    val ay = a.y + s.offsetY
                    if (distancePointToSegmentSquared(px = x, py = y, ax = ax, ay = ay, bx = px, by = py) <= r2) {
                        erased = true
                    }
                }

                if (erased) {
                    touched = true
                    if (current.size >= 2) segments.add(current)
                    current = ArrayList()
                } else {
                    current.add(p)
                }
            }
            if (current.size >= 2) segments.add(current)

            if (!touched) continue

            removedStrokes.add(s)
            iter.remove()
            segments.forEach { seg ->
                val ns = Stroke(
                    id = nextStrokeId++,
                    brush = s.brush,
                    color = s.color,
                    baseWidth = s.baseWidth,
                    points = seg,
                    offsetX = s.offsetX,
                    offsetY = s.offsetY
                )
                addedStrokes.add(ns)
                iter.add(ns)
            }
        }

        if (removedStrokes.isNotEmpty() || addedStrokes.isNotEmpty()) {
            selectedIds.clear()
            selectionBounds = null
            pushAction(removed = removedStrokes, added = addedStrokes)
            invalidate()
            onChanged?.invoke()
        }
    }

    private fun cloneStroke(s: Stroke): Stroke = s.copy(points = s.points.toMutableList())

    private fun pushAction(removed: List<Stroke>, added: List<Stroke>) {
        if (removed.isEmpty() && added.isEmpty()) return
        history.addLast(Action(removed = removed.map(::cloneStroke), added = added.map(::cloneStroke)))
        undone.clear()
    }

    private fun distancePointToSegmentSquared(
        px: Float,
        py: Float,
        ax: Float,
        ay: Float,
        bx: Float,
        by: Float
    ): Float {
        val abx = bx - ax
        val aby = by - ay
        val apx = px - ax
        val apy = py - ay
        val abLen2 = abx * abx + aby * aby
        if (abLen2 <= 0.0001f) {
            val dx = px - ax
            val dy = py - ay
            return dx * dx + dy * dy
        }
        var t = (apx * abx + apy * aby) / abLen2
        if (t < 0f) t = 0f
        if (t > 1f) t = 1f
        val cx = ax + abx * t
        val cy = ay + aby * t
        val dx = px - cx
        val dy = py - cy
        return dx * dx + dy * dy
    }

    private fun beginLasso(x: Float, y: Float) {
        isLassoDrawing = true
        lassoPoints.clear()
        lassoPath.reset()
        lassoPoints.add(x to y)
        lassoPath.moveTo(x, y)
        invalidate()
    }

    private fun updateLasso(x: Float, y: Float) {
        if (!isLassoDrawing) return
        val last = lassoPoints.lastOrNull()
        if (last != null) {
            val dist = abs(x - last.first) + abs(y - last.second)
            if (dist < 2f) return
        }
        lassoPoints.add(x to y)
        lassoPath.lineTo(x, y)
        invalidate()
    }

    private fun endLasso() {
        isLassoDrawing = false
        if (lassoPoints.size < 3) {
            lassoPath.reset()
            lassoPoints.clear()
            invalidate()
            return
        }
        lassoPath.close()

        when (tool) {
            Tool.ERASER_AREA -> {
                val rect = boundsOf(lassoPoints)
                val removed = strokes.filter { s ->
                    s.points.any { p ->
                        val px = p.x + s.offsetX
                        val py = p.y + s.offsetY
                        rect.contains(px, py)
                    }
                }
                strokes.removeAll { s -> removed.any { it.id == s.id } }
                lassoPath.reset()
                lassoPoints.clear()
                if (removed.isNotEmpty()) {
                    pushAction(removed = removed, added = emptyList())
                    onChanged?.invoke()
                }
            }
            else -> {
                val polygon = lassoPoints.toList()
                selectedIds.clear()
                strokes.forEach { s ->
                    val hit = s.points.any { p ->
                        pointInPolygon(p.x + s.offsetX, p.y + s.offsetY, polygon)
                    }
                    if (hit) selectedIds.add(s.id)
                }
                recalcSelectionBounds()
                lassoPath.reset()
                lassoPoints.clear()
            }
        }
        invalidate()
    }

    private fun recalcSelectionBounds() {
        if (selectedIds.isEmpty()) {
            selectionBounds = null
            return
        }
        var minX = Float.POSITIVE_INFINITY
        var minY = Float.POSITIVE_INFINITY
        var maxX = Float.NEGATIVE_INFINITY
        var maxY = Float.NEGATIVE_INFINITY
        strokes.forEach { s ->
            if (!selectedIds.contains(s.id)) return@forEach
            s.points.forEach { p ->
                val x = p.x + s.offsetX
                val y = p.y + s.offsetY
                minX = min(minX, x)
                minY = min(minY, y)
                maxX = max(maxX, x)
                maxY = max(maxY, y)
            }
        }
        if (minX.isFinite() && minY.isFinite() && maxX.isFinite() && maxY.isFinite()) {
            val pad = 16f * resources.displayMetrics.density
            selectionBounds = RectF(minX - pad, minY - pad, maxX + pad, maxY + pad)
        } else {
            selectionBounds = null
        }
    }

    private fun moveSelection(dx: Float, dy: Float) {
        if (selectedIds.isEmpty()) return
        strokes.forEach { s ->
            if (selectedIds.contains(s.id)) {
                s.offsetX += dx
                s.offsetY += dy
            }
        }
        recalcSelectionBounds()
        invalidate()
    }

    private fun widthAtPressure(brush: Brush, base: Float, pressure: Float): Float {
        return when (brush) {
            Brush.FOUNTAIN -> {
                val p = pressure.coerceIn(0f, 1f)
                val minW = max(1f, base * 0.35f)
                val maxW = max(minW + 1f, base * 2.2f)
                (minW + (maxW - minW) * p).coerceAtLeast(1f)
            }
            Brush.MARKER -> max(2f, base * 1.2f)
            Brush.PENCIL -> max(1f, base * 0.8f)
            Brush.HIGHLIGHTER -> max(6f, base * 1.6f)
        }
    }

    private fun cancelCurrentStroke() {
        val s = currentStroke ?: return
        currentStroke = null
        strokes.remove(s)
        invalidate()
    }

    private fun normalizePressure(raw: Float, isStylus: Boolean): Float {
        if (!isStylus) return 1f
        return ((raw - 0.05f) / (1.0f - 0.05f)).coerceIn(0f, 1f)
    }

    private fun pointInPolygon(x: Float, y: Float, polygon: List<Pair<Float, Float>>): Boolean {
        var inside = false
        var j = polygon.size - 1
        for (i in polygon.indices) {
            val xi = polygon[i].first
            val yi = polygon[i].second
            val xj = polygon[j].first
            val yj = polygon[j].second
            val intersect = ((yi > y) != (yj > y)) && (x < (xj - xi) * (y - yi) / (yj - yi + 0.000001f) + xi)
            if (intersect) inside = !inside
            j = i
        }
        return inside
    }

    private fun boundsOf(points: List<Pair<Float, Float>>): RectF {
        var minX = Float.POSITIVE_INFINITY
        var minY = Float.POSITIVE_INFINITY
        var maxX = Float.NEGATIVE_INFINITY
        var maxY = Float.NEGATIVE_INFINITY
        points.forEach { (x, y) ->
            minX = min(minX, x)
            minY = min(minY, y)
            maxX = max(maxX, x)
            maxY = max(maxY, y)
        }
        val left = if (minX.isFinite()) minX else 0f
        val top = if (minY.isFinite()) minY else 0f
        val right = if (maxX.isFinite()) maxX else 0f
        val bottom = if (maxY.isFinite()) maxY else 0f
        return RectF(left, top, right, bottom)
    }
}
