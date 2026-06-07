package io.privacydroid.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Kamera/mikrofon erişimi sırasında ölçülen ağ ve medya korelasyon kaydı.
 *
 * [networkBytesSent]: erişim sonrası 60 saniyede gönderilen byte (TrafficStats delta).
 * [newMediaCreated]: erişim öncesi 5 dakikada MediaStore'a yeni fotoğraf/video eklendi mi?
 * [accessDurationMs]: AppOps/UsageStats'tan gelen süre; 0 = bilinmiyor.
 */
@Entity(
    tableName = "correlation_results",
    indices = [Index(value = ["package_name"]), Index(value = ["created_at"])]
)
data class CorrelationResultEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "package_name")
    val packageName: String,

    @ColumnInfo(name = "app_name")
    val appName: String,

    @ColumnInfo(name = "access_type")
    val accessType: String,          // "CAMERA" veya "MICROPHONE"

    @ColumnInfo(name = "access_start_ms")
    val accessStartMs: Long,

    @ColumnInfo(name = "access_duration_ms")
    val accessDurationMs: Long,

    @ColumnInfo(name = "is_background")
    val isBackground: Boolean,

    @ColumnInfo(name = "network_bytes_sent")
    val networkBytesSent: Long,

    @ColumnInfo(name = "new_media_created")
    val newMediaCreated: Boolean,

    @ColumnInfo(name = "media_file_path")
    val mediaFilePath: String?,

    @ColumnInfo(name = "media_file_size_bytes")
    val mediaFileSizeBytes: Long?,

    @ColumnInfo(name = "suspicion_level")
    val suspicionLevel: String,      // SuspicionLevel.name

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
)
