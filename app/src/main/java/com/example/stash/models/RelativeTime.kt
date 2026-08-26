package com.example.stash.models

import java.util.concurrent.TimeUnit

/**
 * Formats elapsed time since saved into a compact label (e.g., "now", "4h", "3d", "2w").
 */
fun relativeSavedLabel(savedAtEpochMillis: Long, nowMillis: Long): String {
    val elapsed = (nowMillis - savedAtEpochMillis).coerceAtLeast(0L)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(elapsed)
    if (minutes < 1) return "now"
    if (minutes < 60) return "${minutes}m"

    val hours = TimeUnit.MILLISECONDS.toHours(elapsed)
    if (hours < 24) return "${hours}h"

    val days = TimeUnit.MILLISECONDS.toDays(elapsed)
    if (days < 7) return "${days}d"
    if (days < 30) return "${days / 7}w"
    if (days < 365) return "${days / 30}mo"
    return "${days / 365}y"
}
