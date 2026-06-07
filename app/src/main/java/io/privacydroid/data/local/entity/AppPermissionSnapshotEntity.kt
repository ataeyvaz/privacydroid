package io.privacydroid.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Her uygulama güncellemesinden sonra o anki izin listesinin anlık görüntüsü.
 * Zaman içindeki izin değişikliklerini takip etmek için kullanılır.
 *
 * [permissions]: virgülle ayrılmış tam izin adı listesi
 * [versionCode]: karşılaştırma için versiyon kodu
 */
@Entity(
    tableName = "app_permission_snapshots",
    indices = [
        Index(value = ["package_name"]),
        Index(value = ["package_name", "version_code"])
    ]
)
data class AppPermissionSnapshotEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "package_name")
    val packageName: String,

    @ColumnInfo(name = "app_name")
    val appName: String,

    @ColumnInfo(name = "version_code")
    val versionCode: Long,

    @ColumnInfo(name = "version_name")
    val versionName: String,

    @ColumnInfo(name = "permissions")
    val permissions: String,  // virgülle ayrılmış: "android.permission.CAMERA,android.permission.RECORD_AUDIO,..."

    @ColumnInfo(name = "snapshot_date")
    val snapshotDate: Long = System.currentTimeMillis()
) {
    fun permissionSet(): Set<String> =
        permissions.split(",").filter { it.isNotBlank() }.toSet()
}
