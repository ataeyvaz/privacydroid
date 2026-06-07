package io.privacydroid.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.privacydroid.data.local.entity.CorrelationResultEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CorrelationResultDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(result: CorrelationResultEntity)

    @Query("""
        SELECT * FROM correlation_results
        WHERE package_name = :packageName
        ORDER BY created_at DESC
        LIMIT 1
    """)
    fun getLatestForApp(packageName: String): Flow<CorrelationResultEntity?>

    @Query("""
        SELECT * FROM correlation_results
        WHERE package_name = :packageName
        ORDER BY created_at DESC
        LIMIT :limit
    """)
    fun getRecentForApp(packageName: String, limit: Int = 10): Flow<List<CorrelationResultEntity>>

    /** Dashboard için: son N günde HIGH şüphe kaydı olan uygulamalar */
    @Query("""
        SELECT * FROM correlation_results
        WHERE suspicion_level = 'HIGH'
        AND created_at > :afterMs
        ORDER BY created_at DESC
    """)
    fun getHighSuspicionAfter(afterMs: Long): Flow<List<CorrelationResultEntity>>

    /** Bir uygulamanın yüksek şüpheli korelasyonları — tek seferlik (rapor için). */
    @Query("""
        SELECT * FROM correlation_results
        WHERE package_name = :packageName
        AND suspicion_level = 'HIGH'
        AND created_at >= :sinceMs
        ORDER BY created_at DESC
        LIMIT :limit
    """)
    suspend fun getHighSuspicionForAppOnce(
        packageName: String,
        sinceMs: Long,
        limit: Int = 5
    ): List<CorrelationResultEntity>

    @Query("DELETE FROM correlation_results WHERE created_at < :beforeMs")
    suspend fun deleteOlderThan(beforeMs: Long): Int
}
