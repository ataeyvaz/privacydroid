package io.privacydroid.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * İzin verilen (engellenmeyen) DNS sorgularının kaydı.
 *
 * Engelleme istatistikleri detay ekranındaki "İzin Verilenler" sekmesi için
 * kullanılır. "İzin verilen sorgu sayısı saniyede yüzlerce olabilir" — bu yüzden
 * VPN servisi her sorguyu DEĞİL, süreç ömrü boyunca her benzersiz domaini
 * YALNIZCA BİR KEZ yazar (bellekte dedup). Böylece tablo sınırlı kalır.
 *
 * [allowReason]:
 *   - "WHITELIST"      → ad_whitelist.txt'de kayıtlı (CDN/banka/Google vb.)
 *   - "USER_EXCEPTION" → kullanıcı bu uygulamayı engellemeden hariç tuttu
 *   - "NORMAL"         → hiçbir listede yok, normal trafik
 */
@Entity(
    tableName = "allowed_domains",
    indices = [
        Index(value = ["allowed_at"]),
        Index(value = ["package_name"])
    ]
)
data class AllowedDomainEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "domain")
    val domain: String,

    @ColumnInfo(name = "package_name")
    val packageName: String,

    @ColumnInfo(name = "app_name")
    val appName: String,

    /** WHITELIST / USER_EXCEPTION / NORMAL */
    @ColumnInfo(name = "allow_reason")
    val allowReason: String,

    @ColumnInfo(name = "allowed_at")
    val allowedAt: Long = System.currentTimeMillis()
)
