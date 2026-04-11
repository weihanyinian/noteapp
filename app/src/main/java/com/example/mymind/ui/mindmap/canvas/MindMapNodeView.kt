package com.example.mymind.ui.mindmap.canvas

import android.content.Context
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.Gravity
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
        radius = 16f * density
        cardElevation = 0.75f * density
        useCompatPadding = true
        isClickable = true
        isFocusable = true
        strokeWidth = 0
        strokeColor = 0x00000000

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.START
            setPadding((14f * density).toInt())
        }

        titleTextView = TextView(context).apply {
            textSize = 14f
            gravity = Gravity.START
            setSingleLine(false)
            setHorizontallyScrolling(false)
            maxWidth = (260f * density).toInt()
        }

        previewTextView = TextView(context).apply {
            textSize = 12f
            gravity = Gravity.START
            alpha = 0.9f
            setSingleLine(false)
            setHorizontallyScrolling(false)
            maxWidth = (260f * density).toInt()
        }

        noteJumpButton = android.widget.ImageView(context).apply {
            setImageResource(com.example.mymind.R.drawable.ic_mm_note)
            val p = (4f * density).toInt()
            setPadding(p, p, p, p)
            val size = (24f * density).toInt()
            layoutParams = LinearLayout.LayoutParams(size, size).apply {
                topMargin = (6f * density).toInt()
            }
            background = context.obtainStyledAttributes(intArrayOf(android.R.attr.selectableItemBackgroundBorderless)).getDrawable(0)
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
        } else {
            titleTextView.textSize = node.textSizeSp ?: 14f
            titleTextView.setTypeface(null, Typeface.NORMAL)
        }
        cardElevation = if (isSelected) 1.5f * resources.displayMetrics.density else 0.75f * resources.displayMetrics.density
        strokeWidth = if (isSelected) (2f * resources.displayMetrics.density).toInt() else 0
        strokeColor = if (isSelected) (textColor and 0x00FFFFFF) or 0x66000000 else 0x00000000

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
