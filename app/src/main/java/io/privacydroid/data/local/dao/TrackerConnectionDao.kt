package io.privacydroid.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.privacydroid.data.local.entity.TrackerConnectionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackerConnectionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(connection: TrackerConnectionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(connections: List<TrackerConnectionEntity>)

    /** Son 24 saat — Dashboard kartı için */
    @Query("""
        SELECT * FROM tracker_connections
        WHERE detected_at >= :sinceMs
        ORDER BY detected_at DESC
    """)
    fun getRecentConnections(sinceMs: Long): Flow<List<TrackerConnectionEntity>>

    /** Belirli bir uygulama için son 7 gün — AppDetail kartı için */
    @Query("""
        SELECT * FROM tracker_connections
        WHERE package_name = :packageName
        AND detected_at >= :sinceMs
        ORDER BY detected_at DESC
    """)
    fun getConnectionsForApp(
        packageName: String,
        sinceMs: Long
    ): Flow<List<TrackerConnectionEntity>>

    /** AppDetail için domain bazında özet: domain + bağlantı sayısı + toplam byte */
    @Query("""
        SELECT domain, category, COUNT(*) as connectionCount, SUM(bytes_sent) as totalBytes, MAX(detected_at) as lastSeen
        FROM tracker_connections
        WHERE package_name = :packageName
        AND detected_at >= :sinceMs
        GROUP BY domain
        ORDER BY connectionCount DESC
    """)
    suspend fun getDomainSummaryForApp(
        packageName: String,
        sinceMs: Long
    ): List<TrackerDomainSummary>

    /** Dashboard için uygulama bazında özet */
    @Query("""
        SELECT package_name, app_name, domain, category,
               COUNT(*) as connectionCount, SUM(bytes_sent) as totalBytes,
               MAX(detected_at) as lastSeen
        FROM tracker_connections
        WHERE detected_at >= :sinceMs
        GROUP BY package_name, domain
        ORDER BY lastSeen DESC
        LIMIT :limit
    """)
    fun getDashboardSummary(sinceMs: Long, limit: Int = 20): Flow<List<TrackerDashboardItem>>

    /**
     * Tam tracker listesi ekranı için: uygulama + domain bazında özet,
     * ilk görülme zamanı dahil, limitsiz. En son görülen önce.
     */
    @Query("""
        SELECT package_name, app_name, domain, category,
               COUNT(*) as connectionCount, SUM(bytes_sent) as totalBytes,
               MAX(detected_at) as lastSeen, MIN(detected_at) as firstSeen
        FROM tracker_connections
        WHERE detected_at >= :sinceMs
        GROUP BY package_name, domain
        ORDER BY lastSeen DESC
    """)
    fun getFullSummary(sinceMs: Long): Flow<List<TrackerFullItem>>

    /** Logs ekranı için ham kayıtlar */
    @Query("""
        SELECT * FROM tracker_connections
        WHERE detected_at >= :sinceMs
        ORDER BY detected_at DESC
        LIMIT :limit
    """)
    fun getRecentRaw(sinceMs: Long, limit: Int = 100): Flow<List<TrackerConnectionEntity>>

    /** Toplam tracker bağlantısı sayısı (rapor için). */
    @Query("SELECT COUNT(*) FROM tracker_connections WHERE detected_at >= :sinceMs")
    suspend fun countSince(sinceMs: Long): Int

    /** En çok bağlanılan tracker domainleri (rapor için). */
    @Query("""
        SELECT domain, COUNT(*) as connectionCount
        FROM tracker_connections
        WHERE detected_at >= :sinceMs
        GROUP BY domain
        ORDER BY connectionCount DESC
        LIMIT :limit
    """)
    suspend fun topDomainsSince(sinceMs: Long, limit: Int = 5): List<TrackerTopDomain>

    @Query("DELETE FROM tracker_connections WHERE detected_at < :beforeMs")
    suspend fun deleteOlderThan(beforeMs: Long): Int
}

data class TrackerTopDomain(
    val domain: String,
    val connectionCount: Int
)

data class TrackerDomainSummary(
    val domain: String,
    val category: String,
    val connectionCount: Int,
    val totalBytes: Long,
    val lastSeen: Long
)

data class TrackerDashboardItem(
    val package_name: String,
    val app_name: String,
    val domain: String,
    val category: String,
    val connectionCount: Int,
    val totalBytes: Long,
    val lastSeen: Long
)

data class TrackerFullItem(
    val package_name: String,
    val app_name: String,
    val domain: String,
    val category: String,
    val connectionCount: Int,
    val totalBytes: Long,
    val lastSeen: Long,
    val firstSeen: Long
)
