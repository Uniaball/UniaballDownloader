package com.uniaball.downloader.util

import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
private val SYSTEM_ZONE = ZoneId.systemDefault()

fun formatSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var size = bytes.toDouble()
    var unitIndex = 0
    while (size >= 1024 && unitIndex < units.lastIndex) {
        size /= 1024
        unitIndex++
    }
    return String.format(Locale.US, "%.1f %s", size, units[unitIndex])
}

/**
 * 将 ISO8601 时间字符串（如 2026-07-20T15:34:00Z）格式化为 yyyy-MM-dd HH:mm
 * GitHub API 返回的是 UTC 时间，这里转换为系统本地时区（北京时间 UTC+8）后再格式化
 */
fun formatTime(iso: String): String {
    if (iso.isBlank()) return ""
    return runCatching {
        val input = java.time.OffsetDateTime.parse(iso)
        val local = input.atZoneSameInstant(SYSTEM_ZONE)
        local.format(TIME_FORMATTER)
    }.getOrElse { iso }
}
