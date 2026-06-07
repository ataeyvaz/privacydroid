package io.privacydroid.data.source

import android.content.Context
import android.content.SharedPreferences
import android.net.TrafficStats
import dagger.hilt.android.qualifiers.ApplicationContext
import io.privacydroid.domain.model.AppNetworkStats
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/**
 * Uygulama bazlı ağ kullanımını takip eder.
 *
 * NetworkStatsManager sistem iznine ihtiyaç duyduğundan TrafficStats kullanılır.
 * TrafficStats son boot'tan bu yana kümülatif bayt döner.
 * Zaman aralığı hesabı için dönemsel anlık görüntü (snapshot) kaydedilir.
 *
 * Snapshot stratejisi:
 *   - Her taramada tüm UID'lerin mevcut baytları kaydedilir.
 *   - "Son 24 saat" = mevcut - (24 saat önceki snapshot)
 *   - "Son 7 gün"   = mevcut - (7 gün önceki snapshot)
 *   - 8 günden eski snapshot'lar silinir.
 */
@Singleton
class NetworkUsageTracker @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        private const val PREFS_NAME = "net_snapshots"
        private val WINDOW_24H = TimeUnit.HOURS.toMillis(24)
        private val WINDOW_7D  = TimeUnit.DAYS.toMillis(7)
        private val PRUNE_AGE  = TimeUnit.DAYS.toMillis(8)

        // Anomali eşikleri
        private const val ANOMALY_24H_MB = 10L * 1024 * 1024   // 10 MB
    }

    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /** Tüm UID'ler için mevcut snapshot'ı kaydet. Periyodik taramada çağrılır. */
    fun saveSnapshot(uid: Int) {
        val tx = TrafficStats.getUidTxBytes(uid)
        val rx = TrafficStats.getUidRxBytes(uid)
        if (tx == TrafficStats.UNSUPPORTED.toLong() || rx == TrafficStats.UNSUPPORTED.toLong()) return

        val key = "snap_${uid}_${System.currentTimeMillis()}"
        prefs.edit().putString(key, "$tx,$rx").apply()

        pruneOld()
    }

    /** UID için ağ istatistikleri döner. */
    fun getStats(uid: Int): AppNetworkStats {
        val nowTx = getTxBytes(uid)
        val nowRx = getRxBytes(uid)

        if (nowTx == 0L && nowRx == 0L) return AppNetworkStats()

        val snap24h = getSnapshotNear(uid, WINDOW_24H)
        val snap7d  = getSnapshotNear(uid, WINDOW_7D)

        val tx24h = (nowTx - snap24h.first).coerceAtLeast(0L)
        val rx24h = (nowRx - snap24h.second).coerceAtLeast(0L)
        val tx7d  = (nowTx - snap7d.first).coerceAtLeast(0L)
        val rx7d  = (nowRx - snap7d.second).coerceAtLeast(0L)

        val isAnomaly = tx24h > ANOMALY_24H_MB
        val reason = if (isAnomaly) "Son 24 saatte ${(tx24h / 1_048_576.0).let { "%.1f".format(it) }} MB gönderildi" else null

        return AppNetworkStats(
            txBytes24h  = tx24h,
            rxBytes24h  = rx24h,
            txBytes7d   = tx7d,
            rxBytes7d   = rx7d,
            isAnomaly   = isAnomaly,
            anomalyReason = reason
        )
    }

    /** Tüm UID'ler için istatistik döner — Apps listesi için toplu çağrı. */
    fun getBatchStats(uids: List<Int>): Map<Int, AppNetworkStats> =
        uids.associateWith { getStats(it) }

    /** En çok veri gönderen ilk N UID. */
    fun getTopSenders(uids: List<Int>, n: Int = 5): List<Pair<Int, AppNetworkStats>> =
        uids.map { uid -> uid to getStats(uid) }
            .sortedByDescending { (_, stats) -> stats.txBytes24h }
            .take(n)

    // -------------------------------------------------------------------------
    // Private
    // -------------------------------------------------------------------------

    private fun getTxBytes(uid: Int): Long {
        val v = TrafficStats.getUidTxBytes(uid)
        return if (v == TrafficStats.UNSUPPORTED.toLong()) 0L else v
    }

    private fun getRxBytes(uid: Int): Long {
        val v = TrafficStats.getUidRxBytes(uid)
        return if (v == TrafficStats.UNSUPPORTED.toLong()) 0L else v
    }

    private fun getSnapshotNear(uid: Int, windowMs: Long): Pair<Long, Long> {
        val target = System.currentTimeMillis() - windowMs
        val prefix = "snap_${uid}_"

        val best = prefs.all.entries
            .filter { it.key.startsWith(prefix) }
            .minByOrNull { e ->
                val ts = e.key.removePrefix(prefix).toLongOrNull() ?: Long.MAX_VALUE
                abs(ts - target)
            } ?: return Pair(0L, 0L)

        val parts = (best.value as? String)?.split(",") ?: return Pair(0L, 0L)
        return Pair(
            parts.getOrNull(0)?.toLongOrNull() ?: 0L,
            parts.getOrNull(1)?.toLongOrNull() ?: 0L
        )
    }

    private fun pruneOld() {
        val cutoff = System.currentTimeMillis() - PRUNE_AGE
        val toDelete = prefs.all.keys.filter { key ->
            val ts = key.substringAfterLast("_").toLongOrNull() ?: return@filter false
            ts < cutoff
        }
        if (toDelete.isNotEmpty()) {
            prefs.edit().apply { toDelete.forEach { remove(it) } }.apply()
            Timber.d("${toDelete.size} eski network snapshot silindi")
        }
    }
}
