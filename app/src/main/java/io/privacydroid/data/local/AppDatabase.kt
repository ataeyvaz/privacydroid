package io.privacydroid.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import io.privacydroid.data.local.dao.AllowedDomainDao
import io.privacydroid.data.local.dao.AppBlockingExceptionDao
import io.privacydroid.data.local.dao.AppPermissionSnapshotDao
import io.privacydroid.data.local.dao.BlockedDomainDao
import io.privacydroid.data.local.dao.CorrelationResultDao
import io.privacydroid.data.local.dao.NotificationLogDao
import io.privacydroid.data.local.dao.PermissionLogDao
import io.privacydroid.data.local.dao.TrackerConnectionDao
import io.privacydroid.data.local.entity.AllowedDomainEntity
import io.privacydroid.data.local.entity.AppBlockingExceptionEntity
import io.privacydroid.data.local.entity.AppPermissionSnapshotEntity
import io.privacydroid.data.local.entity.BlockedDomainEntity
import io.privacydroid.data.local.entity.CorrelationResultEntity
import io.privacydroid.data.local.entity.NotificationLogEntity
import io.privacydroid.data.local.entity.PermissionLogEntity
import io.privacydroid.data.local.entity.TrackerConnectionEntity

/**
 * Migration kuralı:
 *   İndeks adları Room'un ürettiği formata UYMAK ZORUNDA:
 *   index_{tableName}_{column1}_{column2}
 *
 *   Farklı ad kullanılırsa Room şema doğrulaması
 *   "Migration didn't properly handle" hatası fırlatır.
 *   Bu hata fallbackToDestructiveMigration() tarafından yakalanamaz.
 */
@Database(
    entities = [
        PermissionLogEntity::class,
        CorrelationResultEntity::class,
        AppPermissionSnapshotEntity::class,
        TrackerConnectionEntity::class,
        NotificationLogEntity::class,
        BlockedDomainEntity::class,
        AppBlockingExceptionEntity::class,
        AllowedDomainEntity::class
    ],
    version = 9,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun permissionLogDao(): PermissionLogDao
    abstract fun correlationResultDao(): CorrelationResultDao
    abstract fun appPermissionSnapshotDao(): AppPermissionSnapshotDao
    abstract fun trackerConnectionDao(): TrackerConnectionDao
    abstract fun notificationLogDao(): NotificationLogDao
    abstract fun blockedDomainDao(): BlockedDomainDao
    abstract fun appBlockingExceptionDao(): AppBlockingExceptionDao
    abstract fun allowedDomainDao(): AllowedDomainDao

    companion object {
        const val DATABASE_NAME = "privacydroid.db"

        /** v1 → v2: correlation_results tablosu eklendi. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS `correlation_results` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `package_name` TEXT NOT NULL,
                        `app_name` TEXT NOT NULL,
                        `access_type` TEXT NOT NULL,
                        `access_start_ms` INTEGER NOT NULL,
                        `access_duration_ms` INTEGER NOT NULL,
                        `is_background` INTEGER NOT NULL,
                        `network_bytes_sent` INTEGER NOT NULL,
                        `new_media_created` INTEGER NOT NULL,
                        `media_file_path` TEXT,
                        `media_file_size_bytes` INTEGER,
                        `suspicion_level` TEXT NOT NULL,
                        `created_at` INTEGER NOT NULL
                    )
                """)
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_correlation_results_package_name` " +
                    "ON `correlation_results` (`package_name`)"
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_correlation_results_created_at` " +
                    "ON `correlation_results` (`created_at`)"
                )
            }
        }

        /**
         * v2 → v3:
         *   1. app_permission_snapshots tablosu eklendi.
         *   2. correlation_results indeksleri düzeltildi.
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("DROP INDEX IF EXISTS `idx_corr_pkg`")
                database.execSQL("DROP INDEX IF EXISTS `idx_corr_time`")
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_correlation_results_package_name` " +
                    "ON `correlation_results` (`package_name`)"
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_correlation_results_created_at` " +
                    "ON `correlation_results` (`created_at`)"
                )
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS `app_permission_snapshots` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `package_name` TEXT NOT NULL,
                        `app_name` TEXT NOT NULL,
                        `version_code` INTEGER NOT NULL,
                        `version_name` TEXT NOT NULL,
                        `permissions` TEXT NOT NULL,
                        `snapshot_date` INTEGER NOT NULL
                    )
                """)
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_app_permission_snapshots_package_name` " +
                    "ON `app_permission_snapshots` (`package_name`)"
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_app_permission_snapshots_package_name_version_code` " +
                    "ON `app_permission_snapshots` (`package_name`, `version_code`)"
                )
            }
        }

        /** v3 → v4: tracker_connections tablosu eklendi. */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS `tracker_connections` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `package_name` TEXT NOT NULL,
                        `app_name` TEXT NOT NULL,
                        `domain` TEXT NOT NULL,
                        `category` TEXT NOT NULL,
                        `bytes_sent` INTEGER NOT NULL,
                        `detected_at` INTEGER NOT NULL
                    )
                """)
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_tracker_connections_package_name` " +
                    "ON `tracker_connections` (`package_name`)"
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_tracker_connections_detected_at` " +
                    "ON `tracker_connections` (`detected_at`)"
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_tracker_connections_domain` " +
                    "ON `tracker_connections` (`domain`)"
                )
            }
        }

        /** v4 → v5: permission_logs'a notified kolonu eklendi. */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE `permission_logs` ADD COLUMN `notified` INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        /** v5 → v6: notification_logs tablosu eklendi (uygulama içi bildirim merkezi). */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS `notification_logs` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `package_name` TEXT NOT NULL,
                        `app_name` TEXT NOT NULL,
                        `message` TEXT NOT NULL,
                        `risk_level` TEXT NOT NULL,
                        `detected_at` INTEGER NOT NULL,
                        `is_read` INTEGER NOT NULL DEFAULT 0,
                        `permission_type` TEXT NOT NULL
                    )
                """)
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_notification_logs_package_name` " +
                    "ON `notification_logs` (`package_name`)"
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_notification_logs_detected_at` " +
                    "ON `notification_logs` (`detected_at`)"
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_notification_logs_is_read` " +
                    "ON `notification_logs` (`is_read`)"
                )
            }
        }

        /**
         * v6 → v7: app_permission_snapshots indeks adlarını normalize eder.
         *
         * Eski sürümler bu tablonun indekslerini `idx_snap_pkg` / `idx_snap_ver`
         * adlarıyla oluşturmuştu. @Entity ise Room'un ürettiği
         * `index_app_permission_snapshots_*` adlarını bekliyor. Bu uyumsuzluk,
         * DB v6'da takılı kalan cihazlarda her açılışta
         * "Migration didn't properly handle: app_permission_snapshots"
         * IllegalStateException'ına yol açıyordu.
         *
         * Bu migration eski adlı indeksleri düşürüp doğru adlılarını oluşturur.
         * Tüm durumlar için güvenli (IF EXISTS / IF NOT EXISTS):
         *   - Eski idx_snap_* olan cihaz → düzeltilir
         *   - Zaten doğru adlı olan cihaz → no-op
         */
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("DROP INDEX IF EXISTS `idx_snap_pkg`")
                database.execSQL("DROP INDEX IF EXISTS `idx_snap_ver`")
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_app_permission_snapshots_package_name` " +
                    "ON `app_permission_snapshots` (`package_name`)"
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_app_permission_snapshots_package_name_version_code` " +
                    "ON `app_permission_snapshots` (`package_name`, `version_code`)"
                )
            }
        }

        /**
         * v7 → v8: DNS reklam/tracker engelleme tabloları eklendi.
         *   - blocked_domains: engellenen her DNS sorgusunun kaydı
         *   - app_blocking_exceptions: engellemeden hariç tutulan uygulamalar
         */
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS `blocked_domains` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `domain` TEXT NOT NULL,
                        `package_name` TEXT NOT NULL,
                        `app_name` TEXT NOT NULL,
                        `block_type` TEXT NOT NULL,
                        `blocked_at` INTEGER NOT NULL
                    )
                """)
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_blocked_domains_blocked_at` " +
                    "ON `blocked_domains` (`blocked_at`)"
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_blocked_domains_block_type` " +
                    "ON `blocked_domains` (`block_type`)"
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_blocked_domains_package_name` " +
                    "ON `blocked_domains` (`package_name`)"
                )
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS `app_blocking_exceptions` (
                        `package_name` TEXT NOT NULL,
                        `app_name` TEXT NOT NULL,
                        `added_at` INTEGER NOT NULL,
                        `note` TEXT,
                        PRIMARY KEY(`package_name`)
                    )
                """)
            }
        }

        /**
         * v8 → v9: allowed_domains tablosu eklendi.
         *   "Engelleme İstatistikleri" detay ekranındaki "İzin Verilenler" sekmesi
         *   için izin verilen DNS sorgularını saklar.
         */
        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS `allowed_domains` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `domain` TEXT NOT NULL,
                        `package_name` TEXT NOT NULL,
                        `app_name` TEXT NOT NULL,
                        `allow_reason` TEXT NOT NULL,
                        `allowed_at` INTEGER NOT NULL
                    )
                """)
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_allowed_domains_allowed_at` " +
                    "ON `allowed_domains` (`allowed_at`)"
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_allowed_domains_package_name` " +
                    "ON `allowed_domains` (`package_name`)"
                )
            }
        }
    }
}
