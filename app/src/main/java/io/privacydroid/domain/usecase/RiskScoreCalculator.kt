package io.privacydroid.domain.usecase

import io.privacydroid.data.model.PermissionLog
import io.privacydroid.domain.model.AppDetail
import io.privacydroid.domain.model.DailyAccessCount
import io.privacydroid.domain.model.RiskComponents
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Son 7 günlük log listesinden 0-100 risk skoru ve bileşenlerini hesaplar.
 *
 * Bileşen ağırlıkları:
 *   Arka plan erişim oranı  × 40  (yüksek oran = yüksek risk)
 *   Gece erişim sayısı      × 30  (5+ gece = maksimum puan)
 *   Farklı izin türü sayısı × 20  (5+ tür  = maksimum puan)
 *   Haftalık toplam erişim  × 10  (50+ kez = maksimum puan)
 */
@Singleton
class RiskScoreCalculator @Inject constructor() {

    fun calculate(logs: List<PermissionLog>, packageName: String): AppDetail {
        val appName = logs.firstOrNull()?.appName ?: packageName

        val total = logs.size
        val bgCount = logs.count { it.isBackground }
        val nightCount = logs.count { isNight(it.accessTime) }
        val uniqueTypes = logs.map { it.permissionType.name }.distinct().size

        // Her bileşen 0-1 arasında normalize edilir, ardından ağırlıkla çarpılır
        val backgroundRatio = if (total > 0) bgCount.toFloat() / total else 0f
        val nightNorm = min(nightCount.toFloat() / 5f, 1f)
        val diversityNorm = min(uniqueTypes.toFloat() / 5f, 1f)
        val frequencyNorm = min(total.toFloat() / 50f, 1f)

        val bgScore = backgroundRatio * 40f
        val nightScore = nightNorm * 30f
        val diversityScore = diversityNorm * 20f
        val freqScore = frequencyNorm * 10f

        val totalScore = (bgScore + nightScore + diversityScore + freqScore).roundToInt()
            .coerceIn(0, 100)

        return AppDetail(
            packageName = packageName,
            appName = appName,
            riskScore = totalScore,
            riskComponents = RiskComponents(bgScore, nightScore, diversityScore, freqScore),
            weeklyData = buildWeeklyData(logs),
            totalWeeklyCount = total,
            backgroundCount = bgCount,
            nightCount = nightCount,
            uniquePermissionCount = uniqueTypes
        )
    }

    private fun buildWeeklyData(logs: List<PermissionLog>): List<DailyAccessCount> {
        val dayFmt = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).apply { timeZone = TimeZone.getDefault() }
        val labelFmt = SimpleDateFormat("EEE", Locale("tr")).apply { timeZone = TimeZone.getDefault() }

        // Son 7 güne ait tüm tarih etiketlerini oluştur (boş günler de listede olsun)
        val today = Calendar.getInstance()
        val days = (6 downTo 0).map { offset ->
            Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -offset) }
        }

        val groupedByDay = logs.groupBy { dayFmt.format(it.accessTime) }

        return days.map { cal ->
            val key = dayFmt.format(cal.time)
            val dayLogs = groupedByDay[key] ?: emptyList()
            val byPermission = dayLogs
                .groupBy { it.permissionType.name }
                .mapValues { (_, v) -> v.size }

            DailyAccessCount(
                dayLabel = labelFmt.format(cal.time).take(3),
                dateMs = cal.timeInMillis,
                totalCount = dayLogs.size,
                countsByPermission = byPermission
            )
        }
    }

    private fun isNight(timeMs: Long): Boolean {
        val hour = Calendar.getInstance().apply { timeInMillis = timeMs }.get(Calendar.HOUR_OF_DAY)
        return hour < 6
    }
}
