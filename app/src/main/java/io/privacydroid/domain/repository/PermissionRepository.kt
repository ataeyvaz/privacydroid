package io.privacydroid.domain.repository

import androidx.paging.PagingData
import io.privacydroid.data.local.dao.AppBackgroundCount
import io.privacydroid.data.model.PermissionLog
import io.privacydroid.domain.model.LogFilter
import kotlinx.coroutines.flow.Flow

/**
 * İzin log repository contract'ı.
 * Implementasyon data katmanında, bağımlılık domain katmanına doğru akar.
 */
interface PermissionRepository {

    fun getLogsForApp(packageName: String): Flow<List<PermissionLog>>

    fun getLogsBetween(fromMs: Long, toMs: Long): Flow<List<PermissionLog>>

    fun getRecentLogs(limit: Int = 50): Flow<List<PermissionLog>>

    fun getBackgroundAccessesAfter(afterMs: Long): Flow<List<PermissionLog>>

    /** Tam filtreli sorgu — DB seviyesinde uygulanır. */
    fun getFilteredLogs(filter: LogFilter): Flow<List<PermissionLog>>

    /** AppDetail sonsuz kaydırma için Paging 3 */
    fun getPagedLogsForApp(packageName: String): Flow<PagingData<PermissionLog>>

    /** Risk skoru ve grafik için son [sinceMs]'den itibaren tüm loglar */
    suspend fun getLogsForAppSince(packageName: String, sinceMs: Long): List<PermissionLog>

    suspend fun getBackgroundCountByApp(startMs: Long, endMs: Long): List<AppBackgroundCount>

    suspend fun saveLogs(logs: List<PermissionLog>)

    suspend fun deleteOlderThan(beforeMs: Long): Int
}
