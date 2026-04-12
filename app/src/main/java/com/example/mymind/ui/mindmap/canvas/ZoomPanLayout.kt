package com.example.mymind.ui.mindmap.canvas

import android.content.Context
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.InputDevice
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.ViewConfiguration
import android.widget.FrameLayout
import kotlin.math.max
import kotlin.math.min
import kotlin.math.abs

/**
 * 可缩放/可平移的容器。
 *
 * 设计目标：
 * - 触屏：支持双指缩放、单指/双指平移（可按场景开关）
 * - 手写：触控笔/橡皮优先交给子 View（避免被容器拦截）
 * - 电脑测试：支持鼠标滚轮缩放、右键/中键拖拽平移（不影响左键绘制）
 */
class ZoomPanLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    private var scaleFactor = 1f
    private var translationXInternal = 0f
    private var translationYInternal = 0f
    private val minScale = 0.5f
    private val maxScale = 2.5f

    private var oneFingerPanEnabled: Boolean = true
    private var twoFingerPanEnabled: Boolean = true
    private var windowFixEnabled: Boolean = true

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
    private var activePointerId: Int = MotionEvent.INVALID_POINTER_ID
    private var lastX = 0f
    private var lastY = 0f
    private var isPanning = false
    private var isMultiPanning = false
    private var lastMultiFocusX = 0f
    private var lastMultiFocusY = 0f
    private var isMousePanning = false

    var onTransformChanged: (() -> Unit)? = null

    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val child = getChildAt(0) ?: return false
            val newScale = (scaleFactor * detector.scaleFactor).coerceIn(minScale, maxScale)
            val focusX = detector.focusX
            val focusY = detector.focusY

            val scaleChange = newScale / scaleFactor
            translationXInternal = focusX - (focusX - translationXInternal) * scaleChange
            translationYInternal = focusY - (focusY - translationYInternal) * scaleChange
            scaleFactor = newScale

            applyTransform(child)
            return true
        }
    })

    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDoubleTap(e: MotionEvent): Boolean {
            val child = getChildAt(0)
            if (child is MindMapBoardView) {
                fitToScreen(animated = true)
            } else {
                reset()
            }
            return true
        }
    })

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (ev.source and InputDevice.SOURCE_MOUSE == InputDevice.SOURCE_MOUSE) {
            val isSecondaryDown = ev.buttonState and MotionEvent.BUTTON_SECONDARY != 0
            val isTertiaryDown = ev.buttonState and MotionEvent.BUTTON_TERTIARY != 0
            return isSecondaryDown || isTertiaryDown
        }
        if (ev.pointerCount > 0) {
            val tool = ev.getToolType(0)
            if (tool == MotionEvent.TOOL_TYPE_STYLUS || tool == MotionEvent.TOOL_TYPE_ERASER) {
                return false
            }
        }
        if (ev.pointerCount > 1) return twoFingerPanEnabled
        if (!oneFingerPanEnabled) return false
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                activePointerId = ev.getPointerId(0)
                lastX = ev.x
                lastY = ev.y
                isPanning = false
            }
            MotionEvent.ACTION_MOVE -> {
                if (scaleDetector.isInProgress) return true
                val index = ev.findPointerIndex(activePointerId)
                if (index < 0) return false
                val x = ev.getX(index)
                val y = ev.getY(index)
                val dx = x - lastX
                val dy = y - lastY
                if (!isPanning && (abs(dx) > touchSlop || abs(dy) > touchSlop)) {
                    isPanning = true
                    lastX = x
                    lastY = y
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                activePointerId = MotionEvent.INVALID_POINTER_ID
                isPanning = false
            }
        }
        return false
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val child = getChildAt(0) ?: return false
        if (event.source and InputDevice.SOURCE_MOUSE == InputDevice.SOURCE_MOUSE) {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    val isSecondaryDown = event.buttonState and MotionEvent.BUTTON_SECONDARY != 0
                    val isTertiaryDown = event.buttonState and MotionEvent.BUTTON_TERTIARY != 0
                    isMousePanning = isSecondaryDown || isTertiaryDown
                    if (isMousePanning) {
                        lastX = event.x
                        lastY = event.y
                        return true
                    }
                }
                MotionEvent.ACTION_MOVE -> {
                    if (isMousePanning) {
                        val dx = event.x - lastX
                        val dy = event.y - lastY
                        translationXInternal += dx
                        translationYInternal += dy
                        lastX = event.x
                        lastY = event.y
                        applyTransform(child)
                        return true
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (isMousePanning) {
                        isMousePanning = false
                        return true
                    }
                }
            }
            return super.onTouchEvent(event)
        }
        if (event.pointerCount > 0) {
            val tool = event.getToolType(0)
            if (tool == MotionEvent.TOOL_TYPE_STYLUS || tool == MotionEvent.TOOL_TYPE_ERASER) {
                return super.onTouchEvent(event)
            }
        }
        val consumedGesture = gestureDetector.onTouchEvent(event)
        val consumedScale = scaleDetector.onTouchEvent(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                activePointerId = event.getPointerId(0)
                lastX = event.x
                lastY = event.y
                isPanning = false
                isMultiPanning = false
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                isPanning = false
                if (twoFingerPanEnabled && event.pointerCount >= 2) {
                    isMultiPanning = true
                    lastMultiFocusX = (event.getX(0) + event.getX(1)) * 0.5f
                    lastMultiFocusY = (event.getY(0) + event.getY(1)) * 0.5f
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (event.pointerCount >= 2 && twoFingerPanEnabled) {
                    val focusX = (event.getX(0) + event.getX(1)) * 0.5f
                    val focusY = (event.getY(0) + event.getY(1)) * 0.5f
                    if (isMultiPanning) {
                        val dx = focusX - lastMultiFocusX
                        val dy = focusY - lastMultiFocusY
                        translationXInternal += dx
                        translationYInternal += dy
                        applyTransform(child)
                    } else {
                        isMultiPanning = true
                    }
                    lastMultiFocusX = focusX
                    lastMultiFocusY = focusY
                } else if (oneFingerPanEnabled && !scaleDetector.isInProgress && event.pointerCount == 1) {
                    val index = event.findPointerIndex(activePointerId)
                    if (index >= 0) {
                        val x = event.getX(index)
                        val y = event.getY(index)
                        val dx = x - lastX
                        val dy = y - lastY
                        translationXInternal += dx
                        translationYInternal += dy
                        lastX = x
                        lastY = y
                        isPanning = true
                        applyTransform(child)
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                activePointerId = MotionEvent.INVALID_POINTER_ID
                isPanning = false
                isMultiPanning = false
            }
            MotionEvent.ACTION_POINTER_UP -> {
                if (event.pointerCount <= 2) {
                    isMultiPanning = false
                }
            }
        }

        return consumedScale || consumedGesture || isPanning || isMultiPanning || super.onTouchEvent(event)
    }

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        if (event.source and InputDevice.SOURCE_MOUSE == InputDevice.SOURCE_MOUSE && event.action == MotionEvent.ACTION_SCROLL) {
            val child = getChildAt(0) ?: return false
            val scroll = event.getAxisValue(MotionEvent.AXIS_VSCROLL)
            if (scroll != 0f) {
                val step = 1.10f
                val factor = if (scroll > 0) 1f / step else step
                zoomByAt(factor = factor, focusX = event.x, focusY = event.y, child = child)
                return true
            }
        }
        return super.onGenericMotionEvent(event)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        val child = getChildAt(0) ?: return
        val childWidthSpec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        val childHeightSpec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        child.measure(childWidthSpec, childHeightSpec)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        val child = getChildAt(0) ?: return
        if (child.pivotX != 0f || child.pivotY != 0f) {
            child.pivotX = 0f
            child.pivotY = 0f
        }
        applyTransform(child)
    }

    fun reset() {
        val child = getChildAt(0) ?: return
        scaleFactor = 1f
        translationXInternal = 0f
        translationYInternal = 0f
        applyTransform(child)
    }

    fun currentScale(): Float = scaleFactor

    fun zoomBy(factor: Float) {
        val child = getChildAt(0) ?: return
        val newScale = (scaleFactor * factor).coerceIn(minScale, maxScale)
        val focusX = width / 2f
        val focusY = height / 2f
        val scaleChange = newScale / scaleFactor
        translationXInternal = focusX - (focusX - translationXInternal) * scaleChange
        translationYInternal = focusY - (focusY - translationYInternal) * scaleChange
        scaleFactor = newScale
        applyTransform(child)
    }

    fun zoomTo(targetScale: Float) {
        val child = getChildAt(0) ?: return
        val newScale = targetScale.coerceIn(minScale, maxScale)
        val focusX = width / 2f
        val focusY = height / 2f
        val scaleChange = newScale / scaleFactor
        translationXInternal = focusX - (focusX - translationXInternal) * scaleChange
        translationYInternal = focusY - (focusY - translationYInternal) * scaleChange
        scaleFactor = newScale
        applyTransform(child)
    }

    fun adjustTranslation(deltaX: Float, deltaY: Float) {
        val child = getChildAt(0) ?: return
        translationXInternal += deltaX
        translationYInternal += deltaY
        applyTransform(child)
    }

    fun centerOn(contentX: Float, contentY: Float) {
        val child = getChildAt(0) ?: return
        val targetX = width / 2f - contentX * scaleFactor
        val targetY = height / 2f - contentY * scaleFactor
        translationXInternal = targetX
        translationYInternal = targetY
        applyTransform(child)
    }

    fun setWindowFixEnabled(enabled: Boolean) {
        windowFixEnabled = enabled
        val child = getChildAt(0) ?: return
        applyTransform(child)
    }

    fun fitToScreen(animated: Boolean) {
        val child = getChildAt(0) ?: return
        if (width <= 0 || height <= 0) return

        val bounds = (child as? MindMapBoardView)?.getVisibleBounds()
        if (bounds == null || bounds.isEmpty) {
            if (animated) reset() else {
                scaleFactor = 1f
                translationXInternal = 0f
                translationYInternal = 0f
                applyTransform(child)
            }
            return
        }

        val padding = 24f * resources.displayMetrics.density
        val contentW = max(1f, bounds.width() + padding * 2f)
        val contentH = max(1f, bounds.height() + padding * 2f)
        val targetScale = min(width / contentW, height / contentH).coerceIn(minScale, maxScale)

        val targetTx = width / 2f - bounds.centerX() * targetScale
        val targetTy = height / 2f - bounds.centerY() * targetScale

        if (!animated) {
            scaleFactor = targetScale
            translationXInternal = targetTx
            translationYInternal = targetTy
            applyTransform(child)
            return
        }

        val startScale = scaleFactor
        val startTx = translationXInternal
        val startTy = translationYInternal
        val animator = android.animation.ValueAnimator.ofFloat(0f, 1f)
        animator.duration = 220
        animator.addUpdateListener { a ->
            val f = a.animatedValue as Float
            scaleFactor = startScale + (targetScale - startScale) * f
            translationXInternal = startTx + (targetTx - startTx) * f
            translationYInternal = startTy + (targetTy - startTy) * f
            applyTransform(child)
        }
        animator.start()
    }

    fun setOneFingerPanEnabled(enabled: Boolean) {
        oneFingerPanEnabled = enabled
    }

    fun setTwoFingerPanEnabled(enabled: Boolean) {
        twoFingerPanEnabled = enabled
    }

    private fun zoomByAt(factor: Float, focusX: Float, focusY: Float, child: android.view.View) {
        val newScale = (scaleFactor * factor).coerceIn(minScale, maxScale)
        val scaleChange = newScale / scaleFactor
        translationXInternal = focusX - (focusX - translationXInternal) * scaleChange
        translationYInternal = focusY - (focusY - translationYInternal) * scaleChange
        scaleFactor = newScale
        applyTransform(child)
    }

    private fun applyTransform(child: android.view.View) {
        if (windowFixEnabled) {
            clampTransform(child)
        }
        child.scaleX = scaleFactor
        child.scaleY = scaleFactor
        child.translationX = translationXInternal
        child.translationY = translationYInternal
        onTransformChanged?.invoke()
    }

    private fun clampTransform(child: android.view.View) {
        if (width <= 0 || height <= 0) return
        val margin = 48f * resources.displayMetrics.density
        val scaledW = child.measuredWidth * scaleFactor
        val scaledH = child.measuredHeight * scaleFactor

        val tx = if (scaledW <= width) {
            (width - scaledW) * 0.5f
        } else {
            translationXInternal.coerceIn(width - scaledW - margin, margin)
        }
        val ty = if (scaledH <= height) {
            (height - scaledH) * 0.5f
        } else {
            translationYInternal.coerceIn(height - scaledH - margin, margin)
        }

        translationXInternal = tx
        translationYInternal = ty
    }
}
