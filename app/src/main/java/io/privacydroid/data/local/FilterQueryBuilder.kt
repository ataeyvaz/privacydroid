package io.privacydroid.data.local

import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteQuery
import io.privacydroid.domain.model.LogFilter

/**
 * LogFilter'ı Room'un anlayacağı SupportSQLiteQuery'e dönüştürür.
 *
 * Filtre kombinasyonları AND ile birleştirilir.
 * Gece filtresi SQLite strftime ile yerel saat üzerinden çalışır.
 *   strftime('%H', access_time/1000, 'unixepoch', 'localtime')
 * Android'de bu ifade sistem zaman dilimine göre saat döner.
 */
object FilterQueryBuilder {

    fun build(filter: LogFilter): SupportSQLiteQuery {
        val args = mutableListOf<Any>()
        val conditions = mutableListOf<String>()

        // Zaman aralığı — her zaman uygulanır
        conditions += "access_time BETWEEN ? AND ?"
        args += filter.startMs
        args += filter.endMs

        // İzin türü filtresi
        if (filter.permissionTypes.isNotEmpty()) {
            val placeholders = filter.permissionTypes.joinToString(",") { "?" }
            conditions += "permission_type IN ($placeholders)"
            args.addAll(filter.permissionTypes)
        }

        // Sadece arka plan
        if (filter.backgroundOnly) {
            conditions += "is_background = 1"
        }

        // Sadece gece (00:00–06:00) — SQLite yerel saat
        if (filter.nightOnly) {
            conditions += "CAST(strftime('%H', access_time/1000, 'unixepoch', 'localtime') AS INTEGER) < 6"
        }

        val where = conditions.joinToString(" AND ")
        val sql = "SELECT * FROM permission_logs WHERE $where ORDER BY access_time DESC"

        return SimpleSQLiteQuery(sql, args.toTypedArray())
    }
}
