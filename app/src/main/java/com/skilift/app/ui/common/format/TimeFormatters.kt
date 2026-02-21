package com.skilift.app.ui.common.format

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun formatTime(epochMillis: Long): String {
    val format = SimpleDateFormat("h:mm a", Locale.getDefault())
    return format.format(Date(epochMillis))
}
