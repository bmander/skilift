package com.skilift.app.domain.model

sealed interface TimeSelection {
    data object DepartNow : TimeSelection
    data class DepartAt(val epochMillis: Long) : TimeSelection
    data class ArriveBy(val epochMillis: Long) : TimeSelection
}
