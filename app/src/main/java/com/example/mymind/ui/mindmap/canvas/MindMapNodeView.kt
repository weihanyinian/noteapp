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

    init {
        val density = resources.displayMetrics.density
        radius = 14f * density
        cardElevation = 1.5f * density
        useCompatPadding = true
        isClickable = true
        isFocusable = true
        strokeWidth = (1f * density).toInt()
        strokeColor = 0x22000000.toInt()

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding((14f * density).toInt())
        }

        titleTextView = TextView(context).apply {
            textSize = 14f
            gravity = Gravity.CENTER
        }

        previewTextView = TextView(context).apply {
            textSize = 12f
            gravity = Gravity.CENTER
            alpha = 0.9f
        }

        container.addView(titleTextView)
        container.addView(previewTextView)
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
        cardElevation = if (isSelected) 3f * resources.displayMetrics.density else 1.5f * resources.displayMetrics.density

        titleTextView.text = node.content
        if (node.noteId != null && !notePreview.isNullOrBlank()) {
            previewTextView.text = notePreview
            previewTextView.visibility = VISIBLE
        } else if (node.noteId != null) {
            previewTextView.text = "已绑定笔记"
            previewTextView.visibility = VISIBLE
        } else {
            previewTextView.text = ""
            previewTextView.visibility = GONE
        }
    }
}
