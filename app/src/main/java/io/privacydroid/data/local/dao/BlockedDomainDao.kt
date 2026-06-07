package io.privacydroid.data.local.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.privacydroid.data.local.entity.BlockedDomainEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BlockedDomainDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: BlockedDomainEntity)

    /** Belirli tipte ([blockType]) ve [sinceMs] sonrası engellenen sayısı — istatistik için. */
    @Query("SELECT COUNT(*) FROM blocked_domains WHERE block_type = :blockType AND blocked_at >= :sinceMs")
    fun countByTypeSince(blockType: String, sinceMs: Long): Flow<Int>

    /** Toplam engellenen sayısı (tüm tipler), [sinceMs] sonrası. */
    @Query("SELECT COUNT(*) FROM blocked_domains WHERE blocked_at >= :sinceMs")
    fun countSince(sinceMs: Long): Flow<Int>

    /** Engellenen domainlerin tam listesi (en yeni önce) — detay ekranı için. */
    @Query("SELECT * FROM blocked_domains WHERE blocked_at >= :sinceMs ORDER BY blocked_at DESC LIMIT :limit")
    fun recentBlocked(sinceMs: Long, limit: Int = 200): Flow<List<BlockedDomainEntity>>

    /** Domain bazında özet: domain + tip + sayı (en çok engellenen önce). */
    @Query("""
        SELECT domain, block_type AS blockType, COUNT(*) AS count, MAX(blocked_at) AS lastBlocked
        FROM blocked_domains
        WHERE blocked_at >= :sinceMs
        GROUP BY domain
        ORDER BY count DESC
        LIMIT :limit
    """)
    fun blockedSummary(sinceMs: Long, limit: Int = 100): Flow<List<BlockedDomainSummary>>

    /**
     * Engellenenler — sonsuz kaydırma (Paging 3).
     * Arama: domain veya uygulama adı eşleşmesi (boş arama = hepsi).
     */
    @Query("""
        SELECT * FROM blocked_domains
        WHERE blocked_at >= :sinceMs
        AND (:query = '' OR domain LIKE '%' || :query || '%' OR app_name LIKE '%' || :query || '%')
        ORDER BY blocked_at DESC
    """)
    fun pagedBlocked(sinceMs: Long, query: String): PagingSource<Int, BlockedDomainEntity>

    /** [blockType] tipinde [sinceMs] sonrası engellenen sayısı — tek seferlik (rapor). */
    @Query("SELECT COUNT(*) FROM blocked_domains WHERE block_type = :blockType AND blocked_at >= :sinceMs")
    suspend fun countByTypeSinceOnce(blockType: String, sinceMs: Long): Int

    @Query("DELETE FROM blocked_domains WHERE blocked_at < :beforeMs")
    suspend fun deleteOlderThan(beforeMs: Long): Int
}

data class BlockedDomainSummary(
    val domain: String,
    val blockType: String,
    val count: Int,
    val lastBlocked: Long
)
