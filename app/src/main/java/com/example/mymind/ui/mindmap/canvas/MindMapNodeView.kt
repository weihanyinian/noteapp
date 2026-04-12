package com.example.mymind.ui.mindmap.canvas

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.setPadding
import com.example.mymind.data.local.entity.MindNodeEntity
import com.google.android.material.card.MaterialCardView

class MindMapNodeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : MaterialCardView(context, attrs) {

    private val titleTextView: TextView
    private val previewTextView: TextView
    private val noteJumpButton: android.widget.ImageView
    var onNoteJumpClick: (() -> Unit)? = null

    init {
        val density = resources.displayMetrics.density
        radius = 18f * density
        cardElevation = 2f * density
        useCompatPadding = true
        isClickable = true
        isFocusable = true
        strokeWidth = (1f * density).toInt()
        strokeColor = 0x10FFFFFF
        rippleColor = ColorStateList.valueOf(0x14000000)
        preventCornerOverlap = false

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            minimumWidth = (90f * density).toInt()
            setPadding((14f * density).toInt())
        }
        val centeredParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.CENTER_HORIZONTAL
        }

        titleTextView = TextView(context).apply {
            textSize = 14f
            gravity = Gravity.CENTER
            setSingleLine(false)
            setHorizontallyScrolling(false)
            maxWidth = (170f * density).toInt()
            letterSpacing = 0.01f
            layoutParams = centeredParams
        }

        previewTextView = TextView(context).apply {
            textSize = 11f
            gravity = Gravity.CENTER
            alpha = 0.82f
            setSingleLine(false)
            setHorizontallyScrolling(false)
            maxWidth = (170f * density).toInt()
            layoutParams = centeredParams
        }

        noteJumpButton = android.widget.ImageView(context).apply {
            setImageResource(com.example.mymind.R.drawable.ic_mm_note)
            val p = (3f * density).toInt()
            setPadding(p, p, p, p)
            val size = (20f * density).toInt()
            layoutParams = LinearLayout.LayoutParams(size, size).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                topMargin = (5f * density).toInt()
            }
            val ta = context.obtainStyledAttributes(intArrayOf(android.R.attr.selectableItemBackgroundBorderless))
            background = ta.getDrawable(0)
            ta.recycle()
            isClickable = true
            isFocusable = true
            setOnClickListener {
                onNoteJumpClick?.invoke()
            }
        }

        container.addView(titleTextView)
        container.addView(previewTextView)
        container.addView(noteJumpButton)
        addView(container)
    }

    fun bind(node: MindNodeEntity, notePreview: String?, isSelected: Boolean, resolvedBackgroundColor: Int, resolvedTextColor: Int) {
        val background = resolvedBackgroundColor
        setCardBackgroundColor(background)
        val textColor = resolvedTextColor
        titleTextView.setTextColor(textColor)
        previewTextView.setTextColor(textColor)

        if (node.isRoot) {
            titleTextView.textSize = node.textSizeSp ?: 18f
            titleTextView.setTypeface(null, Typeface.BOLD)
            radius = 22f * resources.displayMetrics.density
        } else {
            titleTextView.textSize = node.textSizeSp ?: 14f
            titleTextView.setTypeface(null, Typeface.BOLD)
            radius = 18f * resources.displayMetrics.density
        }
        cardElevation = if (isSelected) 5f * resources.displayMetrics.density else 2f * resources.displayMetrics.density
        strokeWidth = if (isSelected) (2f * resources.displayMetrics.density).toInt() else (1f * resources.displayMetrics.density).toInt()
        strokeColor = if (isSelected) (textColor and 0x00FFFFFF) or 0x44000000 else 0x10FFFFFF

        titleTextView.text = node.content
        if (node.noteId != null && !notePreview.isNullOrBlank()) {
            previewTextView.text = notePreview
            previewTextView.visibility = VISIBLE
            noteJumpButton.visibility = VISIBLE
            noteJumpButton.setColorFilter(textColor)
        } else if (node.noteId != null) {
            previewTextView.text = "已绑定笔记"
            previewTextView.visibility = VISIBLE
            noteJumpButton.visibility = VISIBLE
            noteJumpButton.setColorFilter(textColor)
        } else {
            previewTextView.text = ""
            previewTextView.visibility = GONE
            noteJumpButton.visibility = VISIBLE
            noteJumpButton.setColorFilter((textColor and 0x00FFFFFF) or 0x88000000.toInt())
        }
    }
}
