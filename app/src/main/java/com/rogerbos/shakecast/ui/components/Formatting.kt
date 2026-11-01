package com.rogerbos.shakecast.ui.components

import androidx.core.text.HtmlCompat
import java.text.DateFormat
import java.util.Date
import java.util.Locale

fun formatClock(ms: Long): String {
    val totalSec = (ms / 1000).coerceAtLeast(0)
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s) else String.format(Locale.US, "%d:%02d", m, s)
}

fun formatDurationShort(sec: Int?): String {
    if (sec == null || sec <= 0) return ""
    val h = sec / 3600
    val m = (sec % 3600) / 60
    return if (h > 0) "${h}h ${m}m" else "${m}m"
}

fun formatDate(ms: Long): String =
    if (ms <= 0) "" else DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(ms))

fun stripHtml(html: String): String =
    HtmlCompat.fromHtml(html, HtmlCompat.FROM_HTML_MODE_COMPACT).toString().trim()
