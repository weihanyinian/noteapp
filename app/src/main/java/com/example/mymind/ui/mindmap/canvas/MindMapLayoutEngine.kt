package com.example.mymind.ui.mindmap.canvas

import com.example.mymind.data.local.entity.MindNodeEntity
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

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
        val root = nodes.firstOrNull { it.isRoot } ?: nodes.minByOrNull { it.depth } ?: return LayoutResult(emptyMap(), emptyMap())

        val childrenByParent: Map<Long, List<MindNodeEntity>> = nodes
            .asSequence()
            .filter { it.parentNodeId != null }
            .filter { visibleNodeIds.contains(it.id) && !it.isDeleted }
            .groupBy { it.parentNodeId!! }
            .mapValues { (_, list) -> list.sortedWith(compareBy<MindNodeEntity> { it.branchOrder }.thenBy { it.id }) }

        val positions = LinkedHashMap<Long, Pair<Float, Float>>(nodes.size)
        val centers = LinkedHashMap<Long, Pair<Float, Float>>(nodes.size)

        fun putNode(nodeId: Long, centerX: Float, centerY: Float) {
            val box = nodeBoxById[nodeId] ?: NodeBox(160f, 72f)
            positions[nodeId] = (centerX - box.width * 0.5f) to (centerY - box.height * 0.5f)
            centers[nodeId] = centerX to centerY
        }

        putNode(root.id, 0f, 0f)

        val rootChildren = childrenByParent[root.id].orEmpty()
        if (rootChildren.isEmpty()) {
            return LayoutResult(positions = positions, centerByNodeId = centers)
        }

        fun layoutSubtree(
            node: MindNodeEntity,
            centerX: Float,
            centerY: Float,
            angleDeg: Float,
            depth: Int
        ) {
            putNode(node.id, centerX, centerY)
            if (node.isCollapsed) return
            val children = childrenByParent[node.id].orEmpty()
            if (children.isEmpty()) return

            val angleRad = Math.toRadians(angleDeg.toDouble())
            val ux = cos(angleRad).toFloat()
            val uy = sin(angleRad).toFloat()
            val nx = -uy
            val ny = ux
            val childGap = when (depth) {
                1 -> verticalGapPx * 0.86f
                2 -> verticalGapPx * 0.74f
                else -> verticalGapPx * 0.66f
            }
            val forwardGap = when (depth) {
                1 -> horizontalGapPx * 0.82f
                2 -> horizontalGapPx * 0.60f
                else -> horizontalGapPx * 0.48f
            }
            val stackCenter = (children.size - 1) * 0.5f

            children.forEachIndexed { index, child ->
                val offset = (index - stackCenter) * childGap
                val childCenterX = centerX + ux * forwardGap + nx * offset
                val childCenterY = centerY + uy * forwardGap + ny * offset
                layoutSubtree(child, childCenterX, childCenterY, angleDeg, depth + 1)
            }
        }

        val startAngle = -70f
        val sweepAngle = 320f
        val angleStep = if (rootChildren.size == 1) 0f else sweepAngle / (rootChildren.size - 1).coerceAtLeast(1)
        val primaryRadius = max(horizontalGapPx * 0.85f, 220f)

        rootChildren.forEachIndexed { index, child ->
            val angle = if (rootChildren.size == 1) 0f else startAngle + angleStep * index
            val rad = Math.toRadians(angle.toDouble())
            val cx = cos(rad).toFloat() * primaryRadius
            val cy = sin(rad).toFloat() * primaryRadius
            layoutSubtree(child, cx, cy, angle, 1)
        }

        if (centers.size > 1) {
            var minY = Float.POSITIVE_INFINITY
            var maxY = Float.NEGATIVE_INFINITY
            centers.forEach { (id, center) ->
                if (!visibleNodeIds.contains(id)) return@forEach
                minY = min(minY, center.second)
                maxY = max(maxY, center.second)
            }
            val shiftY = if (minY.isFinite() && maxY.isFinite()) -((minY + maxY) * 0.5f) else 0f
            if (shiftY != 0f) {
                positions.replaceAll { _, pair ->
                    pair.first to (pair.second + shiftY)
                }
                centers.replaceAll { _, pair ->
                    pair.first to (pair.second + shiftY)
                }
            }
        }

        return LayoutResult(positions = positions, centerByNodeId = centers)
    }
}

