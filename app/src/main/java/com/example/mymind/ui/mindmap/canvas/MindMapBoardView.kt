package com.example.mymind.ui.mindmap.canvas

import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Path
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ViewGroup
import androidx.core.view.children
import com.example.mymind.data.local.entity.MindNodeEntity
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * 思维导图画布（自绘 + 子 View 节点）。
 *
 * 职责划分：
 * - 子节点：MindMapNodeView 负责节点卡片渲染（标题、预览、选中态）
 * - 画布：负责节点布局、连线渲染、套索选择、节点拖拽
 *
 * 布局策略：
 * - 当节点没有保存坐标时，自动生成一套初始坐标
 * - 默认“从左到右”：按层级向右排布，按叶子序纵向分布
 */
class MindMapBoardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : ViewGroup(context, attrs) {

    interface Listener {
        fun onNodeSelected(node: MindNodeEntity)
        fun onNodeDoubleTap(node: MindNodeEntity)
        fun onNodeLongPress(node: MindNodeEntity)
        fun onNodeMoved(nodeId: Long, fromX: Float?, fromY: Float?, toX: Float?, toY: Float?)
        fun onNoteJumpClick(node: MindNodeEntity)
        fun onBlankTap()
    }

    private val nodeViews = LinkedHashMap<Long, MindMapNodeView>()
    private val nodesById = LinkedHashMap<Long, MindNodeEntity>()
    private val notePreviewById = LinkedHashMap<Long, String>()
    private val positionByNodeId = LinkedHashMap<Long, Pair<Float, Float>>()
    private val centerByNodeId = LinkedHashMap<Long, Pair<Float, Float>>()
    private var visibleNodeIds: Set<Long> = emptySet()
    private var layoutOffsetX: Float = 0f
    private var layoutOffsetY: Float = 0f
    private var isNodeDraggingEnabled: Boolean = false
    private var isLassoModeEnabled: Boolean = false
    private val resolvedBackgroundByNodeId = LinkedHashMap<Long, Int>()
    private val resolvedTextColorByNodeId = LinkedHashMap<Long, Int>()
    private val branchBaseColorByNodeId = LinkedHashMap<Long, Int>()

    private var listener: Listener? = null
    private var selectedNodeId: Long? = null

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = 2.25f * resources.displayMetrics.density
        style = Paint.Style.STROKE
        color = 0xFFB0BEC5.toInt()
    }

    private var useCurvedLines: Boolean = true
    private val linePath = Path()
    private val selectionRect = RectF()
    private val dashedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.5f * resources.displayMetrics.density
        color = 0xFF1565C0.toInt()
        pathEffect = DashPathEffect(floatArrayOf(8f * resources.displayMetrics.density, 6f * resources.displayMetrics.density), 0f)
    }

    private val summaryBracketPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = 2.25f * resources.displayMetrics.density
        style = Paint.Style.STROKE
        color = 0xAA546E7A.toInt()
    }
    private val summaryTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xCC37474F.toInt()
        textSize = 12f * resources.displayMetrics.scaledDensity
    }

    private val lassoPath = Path()
    private val lassoPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f * resources.displayMetrics.density
        color = 0xAA1565C0.toInt()
        pathEffect = DashPathEffect(floatArrayOf(6f * resources.displayMetrics.density, 6f * resources.displayMetrics.density), 0f)
    }
    private val lassoPoints = ArrayList<Pair<Float, Float>>()
    private var isLassoDrawing = false

    private val blankGestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            setSelectedNode(null)
            listener?.onBlankTap()
            return true
        }
    })

    init {
        isClickable = true
        isFocusable = true
        clipChildren = false
        clipToPadding = false
    }

    fun setListener(listener: Listener?) {
        this.listener = listener
    }

    fun setUseCurvedLines(enabled: Boolean) {
        useCurvedLines = enabled
        invalidate()
    }

    fun setNodeDraggingEnabled(enabled: Boolean) {
        isNodeDraggingEnabled = enabled
    }

    fun setLassoModeEnabled(enabled: Boolean) {
        isLassoModeEnabled = enabled
        if (!enabled) {
            isLassoDrawing = false
            lassoPath.reset()
            lassoPoints.clear()
            invalidate()
        }
    }

    fun setSelectedNode(nodeId: Long?) {
        if (selectedNodeId == nodeId) return
        selectedNodeId = nodeId
        nodeViews.forEach { (id, view) ->
            val node = nodesById[id]
            if (node != null) {
                val base = branchBaseColorByNodeId[id] ?: 0xFF64B5F6.toInt()
                val bg = resolvedBackgroundByNodeId[id] ?: resolveAutoBackground(node, base)
                val tc = resolvedTextColorByNodeId[id] ?: resolveAutoTextColor(bg)
                view.bind(
                    node = node,
                    notePreview = node.noteId?.let { notePreviewById[it] },
                    isSelected = id == selectedNodeId,
                    resolvedBackgroundColor = bg,
                    resolvedTextColor = tc
                )
            }
        }
        invalidate()
    }

    fun getSelectedNodeId(): Long? = selectedNodeId

    fun getNodeView(nodeId: Long): MindMapNodeView? = nodeViews[nodeId]

    fun getNodeCenter(nodeId: Long): Pair<Float, Float>? {
        val view = nodeViews[nodeId] ?: return null
        val x = view.x + view.width / 2f
        val y = view.y + view.height / 2f
        return x to y
    }

    fun getVisibleBounds(): RectF {
        val out = RectF()
        var hasAny = false
        visibleNodeIds.forEach { id ->
            val v = nodeViews[id] ?: return@forEach
            if (v.visibility != VISIBLE) return@forEach
            val rect = RectF(v.left.toFloat(), v.top.toFloat(), v.right.toFloat(), v.bottom.toFloat())
            if (!hasAny) {
                out.set(rect)
                hasAny = true
            } else {
                out.union(rect)
            }
        }
        if (!hasAny) out.setEmpty()
        return out
    }

    fun submit(nodes: List<MindNodeEntity>, previews: Map<Long, String>) {
        val visibleIds = computeVisibleNodeIds(nodes)
        visibleNodeIds = visibleIds
        resolvedBackgroundByNodeId.clear()
        resolvedTextColorByNodeId.clear()
        branchBaseColorByNodeId.clear()

        nodesById.clear()
        nodes.forEach { nodesById[it.id] = it }

        notePreviewById.clear()
        previews.forEach { (k, v) -> notePreviewById[k] = v }

        val existingIds = nodeViews.keys.toSet()
        val newIds = nodes.map { it.id }.toSet()

        (existingIds - newIds).forEach { removedId ->
            val view = nodeViews.remove(removedId)
            if (view != null) removeView(view)
            positionByNodeId.remove(removedId)
            centerByNodeId.remove(removedId)
        }

        val root = nodes.firstOrNull { it.isRoot } ?: nodes.minByOrNull { it.depth }
        val firstLevel = if (root != null) {
            nodes
                .filter { it.parentNodeId == root.id }
                .sortedWith(compareBy<MindNodeEntity> { it.branchOrder }.thenBy { it.id })
        } else {
            emptyList()
        }
        val branchPalette = intArrayOf(
            0xFFE57373.toInt(),
            0xFF64B5F6.toInt(),
            0xFF81C784.toInt(),
            0xFFFFB74D.toInt(),
            0xFFBA68C8.toInt(),
            0xFF4DB6AC.toInt()
        )
        val branchColorByNodeId = HashMap<Long, Int>()
        firstLevel.forEachIndexed { index, node ->
            branchColorByNodeId[node.id] = branchPalette[index % branchPalette.size]
        }

        fun resolveBranchBaseColor(node: MindNodeEntity): Int {
            if (node.isRoot) return 0xFF1E88E5.toInt()
            val firstLevelAncestor = findFirstLevelAncestorId(node.id)
            return branchColorByNodeId[firstLevelAncestor] ?: 0xFF64B5F6.toInt()
        }

        nodes.forEach { node ->
            val view = nodeViews.getOrPut(node.id) {
                MindMapNodeView(context).also { 
                    it.onNoteJumpClick = {
                        val currentNode = nodesById[node.id]
                        if (currentNode != null) {
                            listener?.onNoteJumpClick(currentNode)
                        }
                    }
                    addView(it) 
                }
            }
            val base = resolveBranchBaseColor(node)
            branchBaseColorByNodeId[node.id] = base
            val bg = node.backgroundColor ?: resolveAutoBackground(node, base)
            val tc = node.textColor ?: resolveAutoTextColor(bg)
            resolvedBackgroundByNodeId[node.id] = bg
            resolvedTextColorByNodeId[node.id] = tc

            view.bind(
                node = node,
                notePreview = node.noteId?.let { notePreviewById[it] },
                isSelected = node.id == selectedNodeId,
                resolvedBackgroundColor = bg,
                resolvedTextColor = tc
            )
            view.visibility = if (visibleIds.contains(node.id) && !node.isDeleted) VISIBLE else GONE
            attachInteractionHandler(view, node.id)

            val existingPos = positionByNodeId[node.id]
            if (existingPos == null) {
                val posX = node.posX
                val posY = node.posY
                if (posX != null && posY != null) {
                    positionByNodeId[node.id] = posX to posY
                }
            }
        }

        ensureAutoLayoutForMissingPositions()
        requestLayout()
        invalidate()
    }

    private fun computeVisibleNodeIds(nodes: List<MindNodeEntity>): Set<Long> {
        if (nodes.isEmpty()) return emptySet()
        val nodesById = nodes.associateBy { it.id }
        val childrenByParent = nodes
            .filter { it.parentNodeId != null }
            .groupBy { it.parentNodeId!! }
            .mapValues { (_, list) -> list.sortedWith(compareBy<MindNodeEntity> { it.branchOrder }.thenBy { it.id }) }

        val root = nodes.firstOrNull { it.isRoot } ?: nodes.minByOrNull { it.depth } ?: return emptySet()
        val result = LinkedHashSet<Long>()
        val stack = ArrayDeque<Long>()
        stack.addLast(root.id)
        while (stack.isNotEmpty()) {
            val id = stack.removeLast()
            val node = nodesById[id] ?: continue
            if (node.isDeleted) continue
            result.add(id)
            if (node.isCollapsed) continue
            val children = childrenByParent[id].orEmpty()
            for (i in children.size - 1 downTo 0) {
                stack.addLast(children[i].id)
            }
        }
        return result
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (isLassoModeEnabled) {
            parent?.requestDisallowInterceptTouchEvent(true)
            return handleLassoTouch(event)
        }
        return blankGestureDetector.onTouchEvent(event) || super.onTouchEvent(event)
    }

    private fun handleLassoTouch(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                isLassoDrawing = true
                lassoPoints.clear()
                lassoPath.reset()
                lassoPoints.add(x to y)
                lassoPath.moveTo(x, y)
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (!isLassoDrawing) return true
                val last = lassoPoints.lastOrNull()
                if (last != null) {
                    val dx = abs(x - last.first)
                    val dy = abs(y - last.second)
                    if (dx + dy < 6f * resources.displayMetrics.density) return true
                }
                lassoPoints.add(x to y)
                lassoPath.lineTo(x, y)
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isLassoDrawing = false
                lassoPath.close()
                selectFirstNodeInLasso()
                lassoPath.reset()
                lassoPoints.clear()
                invalidate()
                return true
            }
        }
        return true
    }

    private fun selectFirstNodeInLasso() {
        if (lassoPoints.size < 3) return
        val polygon = lassoPoints.toList()
        val found = visibleNodeIds
            .mapNotNull { id -> nodeViews[id]?.let { id to it } }
            .firstOrNull { (_, view) ->
                val cx = view.x + view.width / 2f
                val cy = view.y + view.height / 2f
                pointInPolygon(cx, cy, polygon)
            }
        if (found != null) {
            val node = nodesById[found.first]
            setSelectedNode(found.first)
            if (node != null) listener?.onNodeSelected(node)
        }
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

    private fun ensureAutoLayoutForMissingPositions() {
        val nodes = nodesById.values.toList()
        if (nodes.isEmpty()) return

        val root = nodes.firstOrNull { it.isRoot } ?: nodes.minByOrNull { it.depth } ?: return
        val density = resources.displayMetrics.density

        val shouldFullLayout = nodes.none { it.id != root.id && (it.posX != null || it.posY != null) }
        if (shouldFullLayout) {
            positionByNodeId.clear()
            centerByNodeId.clear()
        }

        if (!positionByNodeId.containsKey(root.id)) {
            positionByNodeId[root.id] = 0f to 0f
        }

        val measureSpec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        nodeViews.values.forEach { v ->
            if (v.visibility == VISIBLE) {
                v.measure(measureSpec, measureSpec)
            }
        }

        val maxW = nodeViews
            .filterValues { it.visibility == VISIBLE }
            .values
            .maxOfOrNull { it.measuredWidth }
            ?: (160f * density).roundToInt()
        val maxH = nodeViews
            .filterValues { it.visibility == VISIBLE }
            .values
            .maxOfOrNull { it.measuredHeight }
            ?: (72f * density).roundToInt()
        val horizontalGap = maxW + (220f * density)
        val verticalGap = maxH + (110f * density)
        val rootGroupGap = verticalGap * 0.75f

        val nodeBoxById = HashMap<Long, MindMapLayoutEngine.NodeBox>(nodeViews.size)
        nodeViews.forEach { (id, v) ->
            if (v.visibility != VISIBLE) return@forEach
            nodeBoxById[id] = MindMapLayoutEngine.NodeBox(
                width = v.measuredWidth.toFloat(),
                height = v.measuredHeight.toFloat()
            )
        }

        val engine = MindMapLayoutEngine(
            horizontalGapPx = horizontalGap.toFloat(),
            verticalGapPx = verticalGap.toFloat(),
            rootGroupGapPx = rootGroupGap
        )
        val layout = engine.compute(
            nodes = nodes,
            visibleNodeIds = visibleNodeIds,
            nodeBoxById = nodeBoxById
        )

        layout.positions.forEach { (id, pos) ->
            if (positionByNodeId.containsKey(id) && !shouldFullLayout) return@forEach
            positionByNodeId[id] = pos
        }
        centerByNodeId.clear()
        centerByNodeId.putAll(layout.centerByNodeId)
    }

    private fun attachInteractionHandler(view: MindMapNodeView, nodeId: Long) {
        val touchSlop = 12f * resources.displayMetrics.density
        var downRawX = 0f
        var downRawY = 0f
        var startX = 0f
        var startY = 0f
        var dragging = false
        var fromX: Float? = null
        var fromY: Float? = null

        val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                if (dragging) return false
                val node = nodesById[nodeId] ?: return false
                setSelectedNode(nodeId)
                listener?.onNodeSelected(node)
                view.performClick()
                return true
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                val node = nodesById[nodeId] ?: return false
                setSelectedNode(nodeId)
                listener?.onNodeDoubleTap(node)
                return true
            }

            override fun onLongPress(e: MotionEvent) {
                if (dragging) return
                val node = nodesById[nodeId] ?: return
                setSelectedNode(nodeId)
                listener?.onNodeLongPress(node)
            }
        })

        view.setOnTouchListener { v, event ->
            gestureDetector.onTouchEvent(event)
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = event.rawX
                    downRawY = event.rawY
                    startX = v.x
                    startY = v.y
                    dragging = false
                    fromX = positionByNodeId[nodeId]?.first
                    fromY = positionByNodeId[nodeId]?.second
                    v.parent.requestDisallowInterceptTouchEvent(true)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (!isNodeDraggingEnabled) return@setOnTouchListener true
                    val dx = event.rawX - downRawX
                    val dy = event.rawY - downRawY
                    if (!dragging && (abs(dx) > touchSlop || abs(dy) > touchSlop)) {
                        dragging = true
                    }
                    if (dragging) {
                        val scale = (this@MindMapBoardView.parent as? ZoomPanLayout)?.currentScale() ?: 1f
                        val newX = startX + dx / scale
                        val newY = startY + dy / scale
                        positionByNodeId[nodeId] = (newX - layoutOffsetX) to (newY - layoutOffsetY)
                        requestLayout()
                        invalidate()
                        true
                    } else {
                        true
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.parent.requestDisallowInterceptTouchEvent(false)
                    if (dragging) {
                        val pos = positionByNodeId[nodeId]
                        listener?.onNodeMoved(nodeId, fromX, fromY, pos?.first, pos?.second)
                        true
                    } else {
                        true
                    }
                }
                else -> false
            }
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        var maxChildWidth = 0
        var maxChildHeight = 0

        children.forEach { child ->
            measureChild(child, widthMeasureSpec, heightMeasureSpec)
            maxChildWidth = max(maxChildWidth, child.measuredWidth)
            maxChildHeight = max(maxChildHeight, child.measuredHeight)
        }

        var minX = 0f
        var minY = 0f
        var maxX = 0f
        var maxY = 0f
        positionByNodeId.values.forEach { (x, y) ->
            minX = min(minX, x)
            minY = min(minY, y)
            maxX = max(maxX, x)
            maxY = max(maxY, y)
        }

        val padding = 240f * resources.displayMetrics.density
        layoutOffsetX = (padding / 2f) - minX
        layoutOffsetY = (padding / 2f) - minY
        val contentWidth = (maxX - minX) + maxChildWidth + padding
        val contentHeight = (maxY - minY) + maxChildHeight + padding
        val desiredWidth = contentWidth.roundToInt().coerceAtLeast(1600)
        val desiredHeight = contentHeight.roundToInt().coerceAtLeast(1200)

        val measuredWidth = resolveSize(desiredWidth, widthMeasureSpec)
        val measuredHeight = resolveSize(desiredHeight, heightMeasureSpec)
        setMeasuredDimension(measuredWidth, measuredHeight)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        nodeViews.forEach { (nodeId, view) ->
            val pos = positionByNodeId[nodeId] ?: (0f to 0f)
            val left = (pos.first + layoutOffsetX).roundToInt()
            val top = (pos.second + layoutOffsetY).roundToInt()
            view.layout(left, top, left + view.measuredWidth, top + view.measuredHeight)
        }
    }

    override fun dispatchDraw(canvas: Canvas) {
        drawConnections(canvas)
        super.dispatchDraw(canvas)
        drawCollapsedSummaryHints(canvas)
        drawSelectionOverlay(canvas)
        if (isLassoModeEnabled && isLassoDrawing) {
            canvas.drawPath(lassoPath, lassoPaint)
        }
    }

    private fun drawCollapsedSummaryHints(canvas: Canvas) {
        val density = resources.displayMetrics.density
        val gap = 18f * density
        val braceW = 14f * density

        val childrenByParent = nodesById.values
            .filter { it.parentNodeId != null }
            .groupBy { it.parentNodeId!! }

        nodesById.values.forEach { node ->
            if (!node.isCollapsed) return@forEach
            if (!visibleNodeIds.contains(node.id)) return@forEach
            val hasChildren = childrenByParent[node.id].orEmpty().any { !it.isDeleted }
            if (!hasChildren) return@forEach
            val view = nodeViews[node.id] ?: return@forEach
            if (view.visibility != VISIBLE) return@forEach

            val x = view.x + view.width + gap
            val top = view.y + 6f * density
            val bottom = view.y + view.height - 6f * density
            val mid = (top + bottom) * 0.5f

            linePath.reset()
            linePath.moveTo(x + braceW, top)
            linePath.lineTo(x, top)
            linePath.lineTo(x, bottom)
            linePath.lineTo(x + braceW, bottom)
            canvas.drawPath(linePath, summaryBracketPaint)

            val label = "概要"
            canvas.drawText(label, x + braceW + 10f * density, mid + summaryTextPaint.textSize * 0.35f, summaryTextPaint)
        }
    }

    private fun drawSelectionOverlay(canvas: Canvas) {
        val id = selectedNodeId ?: return
        val view = nodeViews[id] ?: return
        if (view.visibility != VISIBLE) return
        selectionRect.set(view.x, view.y, view.x + view.width, view.y + view.height)
        val r = 14f * resources.displayMetrics.density
        canvas.drawRoundRect(selectionRect, r, r, dashedPaint)
    }

    private fun drawConnections(canvas: Canvas) {
        val density = resources.displayMetrics.density
        val handleBase = 92f * density
        val renderer = SmoothCubicLineRenderer(handleBase)
        nodesById.values.forEach { node ->
            val parentId = node.parentNodeId ?: return@forEach
            if (!visibleNodeIds.contains(node.id) || !visibleNodeIds.contains(parentId)) return@forEach
            val childView = nodeViews[node.id] ?: return@forEach
            val parentView = nodeViews[parentId] ?: return@forEach

            val pcx = parentView.x + parentView.width / 2f
            val pcy = parentView.y + parentView.height / 2f
            val ccx = childView.x + childView.width / 2f
            val ccy = childView.y + childView.height / 2f

            val startX: Float
            val startY: Float
            val endX: Float
            val endY: Float
            val childOnRight = ccx >= pcx
            startX = if (childOnRight) parentView.x + parentView.width else parentView.x
            startY = pcy
            endX = if (childOnRight) childView.x else childView.x + childView.width
            endY = ccy

            val base = branchBaseColorByNodeId[node.id] ?: 0xFF64B5F6.toInt()
            linePaint.color = (base and 0x00FFFFFF) or (0xCC shl 24)

            if (useCurvedLines) {
                renderer.build(linePath, startX, startY, endX, endY)
                canvas.drawPath(linePath, linePaint)
            } else {
                canvas.drawLine(startX, startY, endX, endY, linePaint)
            }
        }
    }

    private fun relaxOverlaps(iterations: Int, movableNodeIds: Set<Long>? = null) {
        val ids = positionByNodeId.keys.toList()
        val density = resources.displayMetrics.density
        val padding = 14f * density
        val sizes = HashMap<Long, Pair<Float, Float>>()
        nodeViews.forEach { (id, v) ->
            sizes[id] = v.measuredWidth.toFloat() to v.measuredHeight.toFloat()
        }
        repeat(iterations) {
            for (i in 0 until ids.size) {
                for (j in i + 1 until ids.size) {
                    val aId = ids[i]
                    val bId = ids[j]
                    if (!visibleNodeIds.contains(aId) || !visibleNodeIds.contains(bId)) continue
                    val ap = positionByNodeId[aId] ?: continue
                    val bp = positionByNodeId[bId] ?: continue
                    val asz = sizes[aId] ?: (180f * density to 70f * density)
                    val bsz = sizes[bId] ?: (180f * density to 70f * density)
                    val ax1 = ap.first
                    val ay1 = ap.second
                    val ax2 = ap.first + asz.first
                    val ay2 = ap.second + asz.second
                    val bx1 = bp.first
                    val by1 = bp.second
                    val bx2 = bp.first + bsz.first
                    val by2 = bp.second + bsz.second
                    val overlapX = min(ax2, bx2) - max(ax1, bx1)
                    val overlapY = min(ay2, by2) - max(ay1, by1)
                    if (overlapX > -padding && overlapY > -padding) {
                        val canMoveA = movableNodeIds == null || movableNodeIds.contains(aId)
                        val canMoveB = movableNodeIds == null || movableNodeIds.contains(bId)
                        if (!canMoveA && !canMoveB) continue

                        val axc = ax1 + asz.first / 2f
                        val ayc = ay1 + asz.second / 2f
                        val bxc = bx1 + bsz.first / 2f
                        val byc = by1 + bsz.second / 2f
                        var vx = bxc - axc
                        var vy = byc - ayc
                        var dist = sqrt(vx * vx + vy * vy)
                        if (dist < 0.001f) {
                            vx = 0f
                            vy = 1f
                            dist = 1f
                        }
                        val push = (min(overlapX, overlapY) + padding) * 0.6f
                        val px = vx / dist * push
                        val py = vy / dist * push

                        if (canMoveA && canMoveB) {
                            positionByNodeId[aId] = (ap.first - px * 0.5f) to (ap.second - py * 0.5f)
                            positionByNodeId[bId] = (bp.first + px * 0.5f) to (bp.second + py * 0.5f)
                        } else if (canMoveB) {
                            positionByNodeId[bId] = (bp.first + px) to (bp.second + py)
                        } else if (canMoveA) {
                            positionByNodeId[aId] = (ap.first - px) to (ap.second - py)
                        }
                    }
                }
            }
        }
    }

    private fun resolveAutoBackground(node: MindNodeEntity, branchBaseColor: Int): Int {
        if (node.isRoot) return 0xFF4A90E2.toInt()
        val depth = max(1, node.depth)
        val factor = when (depth) {
            1 -> 0.56f
            2 -> 0.68f
            else -> 0.76f
        }
        return mixWithWhite(branchBaseColor, factor)
    }

    private fun resolveAutoTextColor(backgroundColor: Int): Int {
        val r = (backgroundColor shr 16) and 0xFF
        val g = (backgroundColor shr 8) and 0xFF
        val b = backgroundColor and 0xFF
        val luminance = (0.2126f * r + 0.7152f * g + 0.0722f * b) / 255f
        return if (luminance < 0.55f) 0xFFFFFFFF.toInt() else 0xFF1F2937.toInt()
    }

    private fun findFirstLevelAncestorId(nodeId: Long): Long {
        var current = nodesById[nodeId] ?: return nodeId
        while (true) {
            val parentId = current.parentNodeId ?: return current.id
            val parent = nodesById[parentId] ?: return current.id
            if (parent.isRoot) return current.id
            current = parent
        }
    }

    private fun mixWithWhite(color: Int, whiteFactor: Float): Int {
        val f = whiteFactor.coerceIn(0f, 1f)
        val r = (color shr 16) and 0xFF
        val g = (color shr 8) and 0xFF
        val b = color and 0xFF
        val rr = (r + (255 - r) * f).toInt().coerceIn(0, 255)
        val gg = (g + (255 - g) * f).toInt().coerceIn(0, 255)
        val bb = (b + (255 - b) * f).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (rr shl 16) or (gg shl 8) or bb
    }
}
