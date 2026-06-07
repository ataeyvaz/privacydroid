package io.privacydroid.data.local.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RawQuery
import androidx.sqlite.db.SupportSQLiteQuery
import io.privacydroid.data.local.entity.PermissionLogEntity
import kotlinx.coroutines.flow.Flow

/**
 * İzin log CRUD operasyonları.
 * Tüm okuma sorguları Flow döner — UI otomatik güncellenir.
 */
@Dao
interface PermissionLogDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(logs: List<PermissionLogEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(log: PermissionLogEntity)

    /** Belirli bir uygulama için tüm izin logları, en yeni önce */
    @Query("""
        SELECT * FROM permission_logs
        WHERE package_name = :packageName
        ORDER BY access_time DESC
    """)
    fun getLogsForApp(packageName: String): Flow<List<PermissionLogEntity>>

    /** Zaman aralığına göre tüm loglar */
    @Query("""
        SELECT * FROM permission_logs
        WHERE access_time BETWEEN :fromMs AND :toMs
        ORDER BY access_time DESC
    """)
    fun getLogsBetween(fromMs: Long, toMs: Long): Flow<List<PermissionLogEntity>>

    /** Belirli izin türü için loglar */
    @Query("""
        SELECT * FROM permission_logs
        WHERE permission_type = :permissionType
        ORDER BY access_time DESC
        LIMIT :limit
    """)
    fun getLogsByPermission(permissionType: String, limit: Int = 100): Flow<List<PermissionLogEntity>>

    /** Son N log — dashboard için */
    @Query("""
        SELECT * FROM permission_logs
        ORDER BY access_time DESC
        LIMIT :limit
    """)
    fun getRecentLogs(limit: Int = 50): Flow<List<PermissionLogEntity>>

    /** Belirli zaman sonrası arka plan erişimleri — şüpheli aktivite tespiti */
    @Query("""
        SELECT * FROM permission_logs
        WHERE is_background = 1
        AND access_time > :afterMs
        ORDER BY access_time DESC
    """)
    fun getBackgroundAccessesAfter(afterMs: Long): Flow<List<PermissionLogEntity>>

    /** Delta tespiti: son tarama zamanından sonraki kayıtlar mevcut mu? */
    @Query("""
        SELECT COUNT(*) FROM permission_logs
        WHERE package_name = :packageName
        AND permission_type = :permissionType
        AND access_time = :accessTime
    """)
    suspend fun existsLog(packageName: String, permissionType: String, accessTime: Long): Int

    /** Uygulama bazında izin erişim sayıları — dashboard özeti */
    @Query("""
        SELECT permission_type, COUNT(*) as count
        FROM permission_logs
        WHERE access_time > :afterMs
        GROUP BY permission_type
    """)
    suspend fun getPermissionCountsAfter(afterMs: Long): List<PermissionTypeCount>

    /**
     * Belirli bir uygulama için son [sinceMs] ms içinde
     * her izin türünün kaç kez kullanıldığını döner.
     * AppDetailSheet'te "güvenle iptal edilebilir mi?" önerisi için.
     */
    @Query("""
        SELECT permission_type, COUNT(*) as count
        FROM permission_logs
        WHERE package_name = :packageName
        AND access_time >= :sinceMs
        GROUP BY permission_type
    """)
    suspend fun getPermissionUsageForApp(
        packageName: String,
        sinceMs: Long
    ): List<PermissionTypeCount>

    /**
     * Bir uygulama için izin türü başına toplam ve arka plan erişim sayıları.
     * Rapor motoru (Faz 3) "47 kez (43'ü arka planda)" satırları için kullanır.
     */
    @Query("""
        SELECT permission_type, COUNT(*) as total, SUM(is_background) as background
        FROM permission_logs
        WHERE package_name = :packageName
        AND access_time >= :sinceMs
        GROUP BY permission_type
        ORDER BY total DESC
    """)
    suspend fun getPermissionBreakdownForApp(
        packageName: String,
        sinceMs: Long
    ): List<PermissionBreakdown>

    /**
     * Bir uygulamanın [sinceMs] sonrası gece (00:00–06:00, yerel saat) arka plan
     * erişim sayısı — rapor "şüpheli aktivite" satırları için.
     */
    @Query("""
        SELECT COUNT(*) FROM permission_logs
        WHERE package_name = :packageName
        AND access_time >= :sinceMs
        AND is_background = 1
        AND CAST(strftime('%H', access_time / 1000, 'unixepoch', 'localtime') AS INTEGER) < 6
    """)
    suspend fun getNightBackgroundCount(packageName: String, sinceMs: Long): Int

    /** Gece arka planda sensöre erişen farklı uygulama sayısı — toplu rapor için. */
    @Query("""
        SELECT COUNT(DISTINCT package_name) FROM permission_logs
        WHERE access_time >= :sinceMs
        AND is_background = 1
        AND CAST(strftime('%H', access_time / 1000, 'unixepoch', 'localtime') AS INTEGER) < 6
    """)
    suspend fun getDistinctNightBackgroundAppCount(sinceMs: Long): Int

    /** Eski logları temizle — LOG_RETENTION_DAYS'den eski kayıtlar */
    @Query("DELETE FROM permission_logs WHERE created_at < :beforeMs")
    suspend fun deleteOlderThan(beforeMs: Long): Int

    /**
     * AppDetail ekranı için sonsuz kaydırma (Paging 3).
     * Room PagingSource'u otomatik olarak sayfalara böler.
     */
    @Query("""
        SELECT * FROM permission_logs
        WHERE package_name = :packageName
        ORDER BY access_time DESC
    """)
    fun getPagedLogsForApp(packageName: String): PagingSource<Int, PermissionLogEntity>

    /** Risk skoru ve haftalık grafik hesabı için son 7 günün logları */
    @Query("""
        SELECT * FROM permission_logs
        WHERE package_name = :packageName
        AND access_time >= :sinceMs
        ORDER BY access_time DESC
    """)
    suspend fun getLogsForAppSince(packageName: String, sinceMs: Long): List<PermissionLogEntity>

    /**
     * Dinamik filtreli sorgu — LogFilter'ın tüm kombinasyonlarını karşılar.
     * [observedEntities] belirtildiğinden tablo değişince Flow yeniden tetiklenir.
     *
     * SQL, QueryBuilder tarafından çalışma zamanında inşa edilir.
     */
    @RawQuery(observedEntities = [PermissionLogEntity::class])
    fun getFilteredLogs(query: SupportSQLiteQuery): Flow<List<PermissionLogEntity>>

    /** Bildirim gönderilen log'u işaretle — tekrar bildirim gönderilmesini engeller */
    @Query("""
        UPDATE permission_logs SET notified = 1
        WHERE package_name = :packageName
        AND permission_type = :permissionType
        AND access_time = :accessTimeMs
    """)
    suspend fun markAsNotified(packageName: String, permissionType: String, accessTimeMs: Long)

    /** Dashboard özet: arka planda erişen uygulama sayısı ve toplam, today */
    @Query("""
        SELECT package_name, COUNT(*) as count
        FROM permission_logs
        WHERE access_time BETWEEN :startMs AND :endMs
        AND is_background = 1
        GROUP BY package_name
        ORDER BY count DESC
    """)
    suspend fun getBackgroundCountByApp(startMs: Long, endMs: Long): List<AppBackgroundCount>
}

data class PermissionTypeCount(
    val permission_type: String,
    val count: Int
)

data class AppBackgroundCount(
    val package_name: String,
    val count: Int
)

data class PermissionBreakdown(
    val permission_type: String,
    val total: Int,
    val background: Int
)
