package io.privacydroid.data.repository

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import io.privacydroid.data.local.FilterQueryBuilder
import io.privacydroid.data.local.dao.AppBackgroundCount
import io.privacydroid.data.local.dao.PermissionLogDao
import io.privacydroid.data.local.entity.PermissionLogEntity
import io.privacydroid.data.model.PermissionLog
import io.privacydroid.data.model.PermissionType
import io.privacydroid.domain.model.LogFilter
import io.privacydroid.domain.repository.PermissionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PermissionRepositoryImpl @Inject constructor(
    private val dao: PermissionLogDao
) : PermissionRepository {

    override fun getLogsForApp(packageName: String): Flow<List<PermissionLog>> =
        dao.getLogsForApp(packageName).map { it.toDomainList() }

    override fun getLogsBetween(fromMs: Long, toMs: Long): Flow<List<PermissionLog>> =
        dao.getLogsBetween(fromMs, toMs).map { it.toDomainList() }

    override fun getRecentLogs(limit: Int): Flow<List<PermissionLog>> =
        dao.getRecentLogs(limit).map { it.toDomainList() }

    override fun getBackgroundAccessesAfter(afterMs: Long): Flow<List<PermissionLog>> =
        dao.getBackgroundAccessesAfter(afterMs).map { it.toDomainList() }

    override fun getFilteredLogs(filter: LogFilter): Flow<List<PermissionLog>> =
        dao.getFilteredLogs(FilterQueryBuilder.build(filter)).map { it.toDomainList() }

    override fun getPagedLogsForApp(packageName: String): Flow<PagingData<PermissionLog>> =
        Pager(
            config = PagingConfig(pageSize = 30, enablePlaceholders = false),
            pagingSourceFactory = { dao.getPagedLogsForApp(packageName) }
        ).flow.map { pagingData -> pagingData.map { it.toDomain() } }

    override suspend fun getLogsForAppSince(
        packageName: String,
        sinceMs: Long
    ): List<PermissionLog> = dao.getLogsForAppSince(packageName, sinceMs).map { it.toDomain() }

    override suspend fun getBackgroundCountByApp(
        startMs: Long,
        endMs: Long
    ): List<AppBackgroundCount> = dao.getBackgroundCountByApp(startMs, endMs)

    override suspend fun saveLogs(logs: List<PermissionLog>) {
        dao.insertAll(logs.map { it.toEntity() })
    }

    override suspend fun deleteOlderThan(beforeMs: Long): Int =
        dao.deleteOlderThan(beforeMs)
}

private fun List<PermissionLogEntity>.toDomainList() = map { it.toDomain() }

private fun PermissionLogEntity.toDomain() = PermissionLog(
    id = id,
    packageName = packageName,
    appName = appName,
    permissionType = PermissionType.fromOpStr(permissionType),
    accessTime = accessTime,
    durationMs = durationMs,
    isBackground = isBackground,
    createdAt = createdAt
)

private fun PermissionLog.toEntity() = PermissionLogEntity(
    id = id,
    packageName = packageName,
    appName = appName,
    permissionType = permissionType.opStr,
    accessTime = accessTime,
    durationMs = durationMs,
    isBackground = isBackground,
    createdAt = createdAt
)
