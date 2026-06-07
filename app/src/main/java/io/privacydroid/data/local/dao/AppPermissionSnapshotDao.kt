package io.privacydroid.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.privacydroid.data.local.entity.AppPermissionSnapshotEntity

@Dao
interface AppPermissionSnapshotDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(snapshot: AppPermissionSnapshotEntity)

    /** En güncel snapshot (en yüksek version_code veya en yeni tarih) */
    @Query("""
        SELECT * FROM app_permission_snapshots
        WHERE package_name = :packageName
        ORDER BY snapshot_date DESC
        LIMIT 1
    """)
    suspend fun getLatest(packageName: String): AppPermissionSnapshotEntity?

    /** Bir paketin tüm snapshot geçmişi — zaman çizelgesi için */
    @Query("""
        SELECT * FROM app_permission_snapshots
        WHERE package_name = :packageName
        ORDER BY snapshot_date ASC
    """)
    suspend fun getHistoryForApp(packageName: String): List<AppPermissionSnapshotEntity>

    /** Mevcut versiyon kodunun snapshot'ı var mı? */
    @Query("""
        SELECT COUNT(*) FROM app_permission_snapshots
        WHERE package_name = :packageName
        AND version_code = :versionCode
    """)
    suspend fun existsForVersion(packageName: String, versionCode: Long): Int

    @Query("DELETE FROM app_permission_snapshots WHERE package_name = :packageName")
    suspend fun deleteForApp(packageName: String)
}
