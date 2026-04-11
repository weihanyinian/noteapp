package com.example.mymind.ui.mindmap.canvas

import android.graphics.Path
import kotlin.math.abs
import kotlin.math.min

internal interface MindMapLineRenderer {
    fun build(path: Path, startX: Float, startY: Float, endX: Float, endY: Float)
}

internal class SmoothCubicLineRenderer(
    private val baseHandle: Float
) : MindMapLineRenderer {
    override fun build(path: Path, startX: Float, startY: Float, endX: Float, endY: Float) {
        path.reset()
        val dx = endX - startX
        val sign = if (dx >= 0f) 1f else -1f
        val handle = min(baseHandle, abs(dx) * 0.5f)
        val c1x = startX + sign * handle
        val c2x = endX - sign * handle
        path.moveTo(startX, startY)
        path.cubicTo(c1x, startY, c2x, endY, endX, endY)
    }
}

