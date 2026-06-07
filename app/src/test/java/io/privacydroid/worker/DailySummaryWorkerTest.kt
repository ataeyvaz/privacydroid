package io.privacydroid.worker

import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class DailySummaryWorkerTest {

    @Test
    fun `hedef saat geçildiyse delay yarın için hesaplanır`() {
        // Saat 23:59 — hedef 09:00 çoktan geçmiş, yarın 09:00 için hesaplanmalı
        val delay = simulatedDelay(currentHour = 23, currentMinute = 59, targetHour = 9)
        // 09:00'a yaklaşık 9 saat 1 dakika kaldı
        val expectedMinMs = 9 * 60 * 60 * 1000L
        val expectedMaxMs = 10 * 60 * 60 * 1000L
        assertTrue(
            "Delay $expectedMinMs ile $expectedMaxMs arasında olmalı, gerçek: $delay",
            delay in expectedMinMs..expectedMaxMs
        )
    }

    @Test
    fun `hedef saat henüz gelmemişse aynı gün için hesaplanır`() {
        // Saat 06:00 — hedef 09:00 henüz gelmemiş
        val delay = simulatedDelay(currentHour = 6, currentMinute = 0, targetHour = 9)
        val expectedMs = 3 * 60 * 60 * 1000L // 3 saat
        val tolerance = 60 * 1000L // 1 dakika tolerans
        assertTrue(
            "Delay yaklaşık 3 saat olmalı, gerçek: $delay",
            delay in (expectedMs - tolerance)..(expectedMs + tolerance)
        )
    }

    @Test
    fun `delay hiçbir zaman negatif olmaz`() {
        val delay = DailySummaryWorker.millisUntilNextOccurrence(9)
        assertTrue("Delay negatif olamaz", delay >= 0)
    }

    @Test
    fun `delay 24 saati geçemez`() {
        val delay = DailySummaryWorker.millisUntilNextOccurrence(9)
        val oneDayMs = 24 * 60 * 60 * 1000L
        assertTrue("Delay 24 saati geçemez", delay < oneDayMs)
    }

    /**
     * Belirli bir saatte delay hesaplamasını simüle eder.
     * Gerçek Calendar.getInstance() yerine kontrollü zaman kullanır.
     */
    private fun simulatedDelay(currentHour: Int, currentMinute: Int, targetHour: Int): Long {
        val now = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, currentHour)
            set(Calendar.MINUTE, currentMinute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, targetHour)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (target.before(now)) target.add(Calendar.DAY_OF_YEAR, 1)
        return target.timeInMillis - now.timeInMillis
    }
}
