package io.privacydroid.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * AppOpsManager'dan elde edilen izin erişim kaydı.
 *
 * [isBackground]: erişim anında uygulama ön planda değilse true.
 * [durationMs]: erişim süresi — AppOps bazı op'lar için 0 döner.
 */
@Entity(
    tableName = "permission_logs",
    indices = [
        Index(value = ["package_name"]),
        Index(value = ["access_time"]),
        Index(value = ["permission_type"])
    ]
)
data class PermissionLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "package_name")
    val packageName: String,

    @ColumnInfo(name = "app_name")
    val appName: String,

    @ColumnInfo(name = "permission_type")
    val permissionType: String,

    @ColumnInfo(name = "access_time")
    val accessTime: Long,

    @ColumnInfo(name = "duration_ms")
    val durationMs: Long = 0L,

    @ColumnInfo(name = "is_background")
    val isBackground: Boolean,

    @ColumnInfo(name = "notified", defaultValue = "0")
    val notified: Boolean = false,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
)
