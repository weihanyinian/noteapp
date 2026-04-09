package com.example.mymind.util

import androidx.core.text.HtmlCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object UiFormatters {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

    fun formatTime(timestamp: Long): String = dateFormat.format(Date(timestamp))

    fun htmlToPlainText(html: String): String {
        return HtmlCompat.fromHtml(html, HtmlCompat.FROM_HTML_MODE_LEGACY)
            .toString()
            .replace("\\s+".toRegex(), " ")
            .trim()
    }
}
