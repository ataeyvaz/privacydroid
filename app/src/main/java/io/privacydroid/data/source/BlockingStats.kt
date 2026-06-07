package io.privacydroid.data.source

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * "Bugün izin verilen DNS sorgusu" sayacı (bellekte).
 *
 * Engellenen reklam/tracker sayıları Room'dan (BlockedDomainDao) okunur — kalıcıdır.
 * İzin verilen sorgu sayısı saniyede yüzlerce olabileceğinden Room'a yazmak yerine
 * bellekte tutulur; süreç yeniden başlayınca sıfırlanır (yaklaşık günlük gösterge).
 *
 * Gün, cihazın yerel saatine göre döner.
 */
@Singleton
class BlockingStats @Inject constructor() {

    private var dayStartMs: Long = todayStartMs()
    private val _allowedToday = MutableStateFlow(0)
    val allowedToday: StateFlow<Int> = _allowedToday

    @Synchronized
    fun recordAllowed() {
        rollIfNeeded()
        _allowedToday.value = _allowedToday.value + 1
    }

    @Synchronized
    private fun rollIfNeeded() {
        if (System.currentTimeMillis() >= dayStartMs + DAY_MS) {
            dayStartMs = todayStartMs()
            _allowedToday.value = 0
        }
    }

    private fun todayStartMs(): Long {
        val cal = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }

    companion object {
        private const val DAY_MS = 24L * 60 * 60 * 1000
    }
}
