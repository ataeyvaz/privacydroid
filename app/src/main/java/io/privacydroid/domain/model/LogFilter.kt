package io.privacydroid.domain.model

import java.util.Calendar
import java.util.concurrent.TimeUnit

data class LogFilter(
    val timeRange: TimeRange = TimeRange.TODAY,
    val permissionTypes: Set<String> = emptySet(), // boş = hepsi
    val backgroundOnly: Boolean = false,
    val nightOnly: Boolean = false,        // 00:00–06:00 arası erişimler
    val showTrackerOnly: Boolean = false   // Tracker bağlantı logları
) {
    val startMs: Long get() = timeRange.startMs()
    val endMs: Long get() = System.currentTimeMillis()
}

enum class TimeRange(val displayName: String) {
    TODAY("Bugün"),
    THIS_WEEK("Bu Hafta"),
    THIS_MONTH("Bu Ay");

    fun startMs(): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
        when (this@TimeRange) {
            TODAY -> Unit
            THIS_WEEK -> set(Calendar.DAY_OF_WEEK, firstDayOfWeek)
            THIS_MONTH -> set(Calendar.DAY_OF_MONTH, 1)
        }
    }.timeInMillis
}
