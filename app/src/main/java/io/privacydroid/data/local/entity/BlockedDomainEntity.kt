package io.privacydroid.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Engellenen her DNS sorgusunun kaydı.
 * "Neden açılmıyor?" sorusuna cevap verebilmek için her NXDOMAIN kararı loglanır.
 *
 * [blockType]: "AD" veya "TRACKER"
 */
@Entity(
    tableName = "blocked_domains",
    indices = [
        Index(value = ["blocked_at"]),
        Index(value = ["block_type"]),
        Index(value = ["package_name"])
    ]
)
data class BlockedDomainEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "domain")
    val domain: String,

    @ColumnInfo(name = "package_name")
    val packageName: String,

    @ColumnInfo(name = "app_name")
    val appName: String,

    /** AD / TRACKER */
    @ColumnInfo(name = "block_type")
    val blockType: String,

    @ColumnInfo(name = "blocked_at")
    val blockedAt: Long = System.currentTimeMillis()
)
