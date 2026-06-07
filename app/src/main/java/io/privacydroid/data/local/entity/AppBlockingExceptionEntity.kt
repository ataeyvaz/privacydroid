package io.privacydroid.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Reklam/tracker engellemesinden HARİÇ tutulan uygulamalar.
 * Bu listedeki uygulamaların DNS sorguları beyaz liste gibi davranır —
 * HİÇBİR ZAMAN engellenmez. Kullanıcı bir uygulama açılmadığında engellemeyi
 * o uygulama için kapatabilir.
 *
 * Paket adı doğal anahtar (PK) — toggle aç/kapa için insert/delete yeterli.
 */
@Entity(tableName = "app_blocking_exceptions")
data class AppBlockingExceptionEntity(
    @PrimaryKey
    @ColumnInfo(name = "package_name")
    val packageName: String,

    @ColumnInfo(name = "app_name")
    val appName: String,

    @ColumnInfo(name = "added_at")
    val addedAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "note")
    val note: String? = null
)
