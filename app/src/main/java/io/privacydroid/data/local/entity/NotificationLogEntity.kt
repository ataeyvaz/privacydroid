package io.privacydroid.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Uygulama içi bildirim merkezi için kayıt.
 * Sistem çubuğuna gitmesi gerekmeyen (HIGH/LOW risk) tespitler buraya kaydedilir.
 * CRITICAL tespitler hem buraya hem sistem çubuğuna gider.
 */
@Entity(
    tableName = "notification_logs",
    indices = [
        Index(value = ["package_name"]),
        Index(value = ["detected_at"]),
        Index(value = ["is_read"])
    ]
)
data class NotificationLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "package_name")
    val packageName: String,

    @ColumnInfo(name = "app_name")
    val appName: String,

    @ColumnInfo(name = "message")
    val message: String,

    /** CRITICAL / HIGH / LOW */
    @ColumnInfo(name = "risk_level")
    val riskLevel: String,

    @ColumnInfo(name = "detected_at")
    val detectedAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "is_read", defaultValue = "0")
    val isRead: Boolean = false,

    @ColumnInfo(name = "permission_type")
    val permissionType: String
)
