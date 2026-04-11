package com.example.mymind.ui.mindmap.canvas

import com.example.mymind.data.local.entity.MindNodeEntity
import kotlin.math.max
import kotlin.math.min

internal class MindMapLayoutEngine(
    private val horizontalGapPx: Float,
    private val verticalGapPx: Float,
    private val rootGroupGapPx: Float
) {
    data class NodeBox(
        val width: Float,
        val height: Float
    )

    data class LayoutResult(
        val positions: Map<Long, Pair<Float, Float>>,
        val centerByNodeId: Map<Long, Pair<Float, Float>>
    )

    fun compute(
        nodes: List<MindNodeEntity>,
        visibleNodeIds: Set<Long>,
        nodeBoxById: Map<Long, NodeBox>
    ): LayoutResult {
        if (nodes.isEmpty()) return LayoutResult(emptyMap(), emptyMap())
        val nodesById = nodes.associateBy { it.id }
        val root = nodes.firstOrNull { it.isRoot } ?: nodes.minByOrNull { it.depth } ?: return LayoutResult(emptyMap(), emptyMap())

        val childrenByParent: Map<Long, List<MindNodeEntity>> = nodes
            .asSequence()
            .filter { it.parentNodeId != null }
            .filter { visibleNodeIds.contains(it.id) && !it.isDeleted }
            .groupBy { it.parentNodeId!! }
            .mapValues { (_, list) -> list.sortedWith(compareBy<MindNodeEntity> { it.branchOrder }.thenBy { it.id }) }

        val centerYByNodeId = HashMap<Long, Float>(nodes.size)
        var cursorY = 0f

        fun layoutCenterY(nodeId: Long): Float {
            centerYByNodeId[nodeId]?.let { return it }
            val node = nodesById[nodeId] ?: return cursorY.also { cursorY += verticalGapPx }
            val children = childrenByParent[nodeId].orEmpty()
            val centerY = if (node.isCollapsed || children.isEmpty()) {
                val y = cursorY
                cursorY += verticalGapPx
                y
            } else if (node.id == root.id) {
                children.forEachIndexed { index, child ->
                    layoutCenterY(child.id)
                    if (index != children.lastIndex) cursorY += rootGroupGapPx
                }
                val first = centerYByNodeId[children.first().id] ?: 0f
                val last = centerYByNodeId[children.last().id] ?: first
                (first + last) * 0.5f
            } else {
                val first = layoutCenterY(children.first().id)
                val last = layoutCenterY(children.last().id)
                (first + last) * 0.5f
            }
            centerYByNodeId[nodeId] = centerY
            return centerY
        }

        layoutCenterY(root.id)

        var minY = Float.POSITIVE_INFINITY
        var maxY = Float.NEGATIVE_INFINITY
        centerYByNodeId.forEach { (id, y) ->
            if (!visibleNodeIds.contains(id)) return@forEach
            minY = min(minY, y)
            maxY = max(maxY, y)
        }
        val shiftY = if (minY.isFinite() && maxY.isFinite()) -((minY + maxY) * 0.5f) else 0f

        val positions = LinkedHashMap<Long, Pair<Float, Float>>(nodes.size)
        val centers = LinkedHashMap<Long, Pair<Float, Float>>(nodes.size)
        val depthSorted = nodes
            .asSequence()
            .filter { visibleNodeIds.contains(it.id) && !it.isDeleted }
            .sortedWith(compareBy<MindNodeEntity> { it.depth }.thenBy { it.branchOrder }.thenBy { it.id })
            .toList()

        depthSorted.forEach { node ->
            val box = nodeBoxById[node.id] ?: NodeBox(160f, 72f)
            val depth = max(0, node.depth)
            val centerX = depth * horizontalGapPx
            val centerY = (centerYByNodeId[node.id] ?: 0f) + shiftY
            val left = centerX - box.width * 0.5f
            val top = centerY - box.height * 0.5f
            positions[node.id] = left to top
            centers[node.id] = centerX to centerY
        }

        return LayoutResult(positions = positions, centerByNodeId = centers)
    }
}

