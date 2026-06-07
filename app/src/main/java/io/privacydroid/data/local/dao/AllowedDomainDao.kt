package io.privacydroid.data.local.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.privacydroid.data.local.entity.AllowedDomainEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AllowedDomainDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: AllowedDomainEntity)

    /** [sinceMs] sonrası izin verilen benzersiz domain sayısı — istatistik için. */
    @Query("SELECT COUNT(*) FROM allowed_domains WHERE allowed_at >= :sinceMs")
    fun countSince(sinceMs: Long): Flow<Int>

    /**
     * İzin verilenler — sonsuz kaydırma (Paging 3).
     * Arama: domain veya uygulama adı eşleşmesi (boş arama = hepsi).
     */
    @Query("""
        SELECT * FROM allowed_domains
        WHERE allowed_at >= :sinceMs
        AND (:query = '' OR domain LIKE '%' || :query || '%' OR app_name LIKE '%' || :query || '%')
        ORDER BY allowed_at DESC
    """)
    fun pagedAllowed(sinceMs: Long, query: String): PagingSource<Int, AllowedDomainEntity>

    /** Süreç ömrü boyunca aynı domainin tekrar yazılmaması için var olanı kontrol eder. */
    @Query("SELECT EXISTS(SELECT 1 FROM allowed_domains WHERE domain = :domain)")
    suspend fun exists(domain: String): Boolean

    @Query("DELETE FROM allowed_domains WHERE allowed_at < :beforeMs")
    suspend fun deleteOlderThan(beforeMs: Long): Int
}
