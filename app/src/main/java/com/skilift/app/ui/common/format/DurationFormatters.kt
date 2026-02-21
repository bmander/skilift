package com.skilift.app.ui.common.format

fun formatDurationMinutes(seconds: Int): String {
    val totalMinutes = seconds / 60
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) {
        "%d:%02d".format(hours, minutes)
    } else {
        "$totalMinutes min"
    }
}
