package com.example.mymind.ui.note.handwriting

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.graphics.withSave
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs
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

    enum class Tool {
        PEN,
        ERASER_POINT,
        ERASER_AREA,
        LASSO
    }

    enum class Brush {
        FOUNTAIN,
        MARKER,
        PENCIL
    }

    private data class InkPoint(val x: Float, val y: Float, val pressure: Float)

    private data class Stroke(
        val id: Long,
        val color: Int,
        val baseWidth: Float,
        val points: MutableList<InkPoint>,
        var offsetX: Float = 0f,
        var offsetY: Float = 0f
    )

    private var nextStrokeId: Long = 1
    private val strokes = ArrayList<Stroke>()
    private val undone = ArrayDeque<Stroke>()
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
    private var isMovingSelection = false
    private var selectionBounds: RectF? = null

    var onChanged: (() -> Unit)? = null

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

    /** 撤销：移除最后一条笔画并放入 undone 栈。 */
    fun undo() {
        if (strokes.isEmpty()) return
        val stroke = strokes.removeLast()
        undone.addLast(stroke)
        selectedIds.remove(stroke.id)
        recalcSelectionBounds()
        invalidate()
        onChanged?.invoke()
    }

    /** 重做：从 undone 栈取出并恢复到 strokes。 */
    fun redo() {
        val stroke = undone.removeLastOrNull() ?: return
        strokes.add(stroke)
        invalidate()
        onChanged?.invoke()
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
        strokes.removeAll { ids.contains(it.id) }
        selectedIds.clear()
        selectionBounds = null
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
                strokes.add(Stroke(id = id, color = color, baseWidth = baseWidth, points = pts, offsetX = offsetX, offsetY = offsetY))
                nextStrokeId = max(nextStrokeId, id + 1)
            }
        }
        invalidate()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val index = event.actionIndex
        val toolType = event.getToolType(index)
        val isStylus = toolType == MotionEvent.TOOL_TYPE_STYLUS || toolType == MotionEvent.TOOL_TYPE_ERASER
        val x = event.x
        val y = event.y

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
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
                    Tool.PEN -> {
                        startStroke(x, y, pressure = normalizePressure(event.pressure, isStylus))
                        return true
                    }
                    Tool.ERASER_POINT -> {
                        eraseAt(x, y)
                        return true
                    }
                    Tool.ERASER_AREA -> {
                        beginLasso(x, y)
                        return true
                    }
                    Tool.LASSO -> {
                        beginLasso(x, y)
                        return true
                    }
                }
            }
            MotionEvent.ACTION_MOVE -> {
                parent?.requestDisallowInterceptTouchEvent(true)
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
                    Tool.PEN -> {
                        appendStroke(x, y, pressure = normalizePressure(event.pressure, isStylus))
                        return true
                    }
                    Tool.ERASER_POINT -> {
                        eraseAt(x, y)
                        return true
                    }
                    Tool.ERASER_AREA -> {
                        updateLasso(x, y)
                        return true
                    }
                    Tool.LASSO -> {
                        updateLasso(x, y)
                        return true
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
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
            }
        }
        return super.onTouchEvent(event)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (ruledEnabled) {
            val top = ruledTopPaddingPx
            val spacing = ruledSpacingPx
            val w = width.toFloat()
            val h = height.toFloat()
            var y = top
            while (y <= h) {
                canvas.drawLine(0f, y, w, y, ruledLinePaint)
                y += spacing
            }
            canvas.drawLine(ruledLeftMarginPx, 0f, ruledLeftMarginPx, h, marginLinePaint)
        }

        strokes.forEach { s ->
            inkPaint.color = when (brush) {
                Brush.FOUNTAIN -> s.color
                Brush.MARKER -> (s.color and 0x00FFFFFF) or 0xAA000000.toInt()
                Brush.PENCIL -> (s.color and 0x00FFFFFF) or 0x88000000.toInt()
            }
            val pts = s.points
            if (pts.size == 1) {
                val p = pts[0]
                val w = widthAtPressure(s.baseWidth, p.pressure)
                inkPaint.strokeWidth = w
                canvas.drawPoint(p.x + s.offsetX, p.y + s.offsetY, inkPaint)
                return@forEach
            }
            for (i in 1 until pts.size) {
                val p0 = pts[i - 1]
                val p1 = pts[i]
                val w = widthAtPressure(s.baseWidth, (p0.pressure + p1.pressure) * 0.5f)
                inkPaint.strokeWidth = w
                canvas.drawLine(p0.x + s.offsetX, p0.y + s.offsetY, p1.x + s.offsetX, p1.y + s.offsetY, inkPaint)
            }
        }

        if (selectedIds.isNotEmpty()) {
            selectionBounds?.let { rect ->
                canvas.drawRoundRect(rect, 16f * resources.displayMetrics.density, 16f * resources.displayMetrics.density, selectionPaint)
            }
        }

        if (isLassoDrawing && lassoPoints.size > 1) {
            canvas.withSave {
                drawPath(lassoPath, lassoPaint)
            }
        }
    }

    private fun startStroke(x: Float, y: Float, pressure: Float) {
        clearSelection()
        undone.clear()
        val stroke = Stroke(
            id = nextStrokeId++,
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
        currentStroke = null
        invalidate()
        onChanged?.invoke()
    }

    private fun eraseAt(x: Float, y: Float) {
        val r = eraserRadiusPx
        val r2 = r * r
        val removed = strokes.removeAll { s ->
            val pts = s.points
            if (pts.isEmpty()) return@removeAll false
            if (pts.size == 1) {
                val p = pts[0]
                val px = p.x + s.offsetX
                val py = p.y + s.offsetY
                val dx = px - x
                val dy = py - y
                return@removeAll dx * dx + dy * dy <= r2
            }
            for (i in 1 until pts.size) {
                val a = pts[i - 1]
                val b = pts[i]
                val ax = a.x + s.offsetX
                val ay = a.y + s.offsetY
                val bx = b.x + s.offsetX
                val by = b.y + s.offsetY
                if (distancePointToSegmentSquared(px = x, py = y, ax = ax, ay = ay, bx = bx, by = by) <= r2) return@removeAll true
            }
            false
        }
        if (removed) {
            selectedIds.clear()
            selectionBounds = null
            invalidate()
            onChanged?.invoke()
        }
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
                val removed = strokes.removeAll { s ->
                    s.points.any { p ->
                        val px = p.x + s.offsetX
                        val py = p.y + s.offsetY
                        rect.contains(px, py)
                    }
                }
                lassoPath.reset()
                lassoPoints.clear()
                if (removed) onChanged?.invoke()
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

    private fun widthAtPressure(base: Float, pressure: Float): Float {
        return when (brush) {
            Brush.FOUNTAIN -> {
                val p = pressure.coerceIn(0f, 1f)
                val w = penMinWidthPx + (penMaxWidthPx - penMinWidthPx) * p
                max(1f, w.coerceAtLeast(base * 0.35f))
            }
            Brush.MARKER -> max(2f, base * 1.2f)
            Brush.PENCIL -> max(1f, base * 0.8f)
        }
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
