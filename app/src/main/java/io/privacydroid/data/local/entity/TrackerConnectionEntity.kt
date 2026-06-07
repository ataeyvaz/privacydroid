package io.privacydroid.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "tracker_connections",
    indices = [
        Index(value = ["package_name"]),
        Index(value = ["detected_at"]),
        Index(value = ["domain"])
    ]
)
data class TrackerConnectionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "package_name")
    val packageName: String,

    @ColumnInfo(name = "app_name")
    val appName: String,

    @ColumnInfo(name = "domain")
    val domain: String,

    @ColumnInfo(name = "category")
    val category: String,           // TrackerCategory.name

    @ColumnInfo(name = "bytes_sent")
    val bytesSent: Long = 0L,

    @ColumnInfo(name = "detected_at")
    val detectedAt: Long = System.currentTimeMillis()
)
