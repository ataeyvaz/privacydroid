package io.privacydroid.ui.common

import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * Detay ekranlarındaki "Bugün / Bu Hafta / Bu Ay" zaman filtresi.
 * [sinceMs] o aralığın başlangıç zaman damgasını verir.
 */
enum class TimeRange(val label: String) {
    TODAY("Bugün"),
    WEEK("Bu Hafta"),
    MONTH("Bu Ay");

    fun sinceMs(now: Long = System.currentTimeMillis()): Long = when (this) {
        TODAY -> Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        WEEK -> now - TimeUnit.DAYS.toMillis(7)
        MONTH -> now - TimeUnit.DAYS.toMillis(30)
    }
}
