package io.privacydroid.domain.usecase

import android.content.Context
import android.content.pm.PackageManager
import dagger.hilt.android.qualifiers.ApplicationContext
import io.privacydroid.data.local.dao.AppPermissionSnapshotDao
import io.privacydroid.data.local.entity.AppPermissionSnapshotEntity
import io.privacydroid.domain.model.resolvePermissionDisplay
import io.privacydroid.util.NotificationHelper
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

data class PermissionChanges(
    val packageName: String,
    val appName: String,
    val versionName: String,
    val added: List<String>,
    val removed: List<String>,
    val isFirstInstall: Boolean
)

/**
 * Her WorkManager taramasında uygulama izin listelerini önceki anlık görüntüyle karşılaştırır.
 *
 * Yeni izin tespit edilince yüksek öncelikli bildirim gönderilir.
 * Kaldırılan izin için sessiz düşük öncelikli bildirim gönderilir.
 */
@Singleton
class PermissionChangeDetector @Inject constructor(
    @ApplicationContext private val context: Context,
    private val snapshotDao: AppPermissionSnapshotDao,
    private val notificationHelper: NotificationHelper
) {

    private val pm = context.packageManager

    /**
     * Kurulu tüm kullanıcı uygulamalarını tarar.
     * Değişiklik tespit edilince bildirim gönderir ve yeni snapshot kaydeder.
     */
    suspend fun detectAndNotify() {
        val packages = pm.getInstalledPackages(PackageManager.GET_PERMISSIONS)
        var detected = 0

        for (pkg in packages) {
            val appInfo = pkg.applicationInfo ?: continue
            val isSystemApp = (appInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
            val isUpdated = (appInfo.flags and android.content.pm.ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
            if (isSystemApp && !isUpdated) continue

            try {
                val result = checkApp(pkg)
                if (result != null && !result.isFirstInstall) {
                    notifyChanges(result)
                    detected++
                }
            } catch (e: Exception) {
                Timber.w("Snapshot karşılaştırma hatası [${pkg.packageName}]: ${e.message}")
            }
        }

        if (detected > 0) Timber.i("$detected uygulamada izin değişikliği tespit edildi")
    }

    private suspend fun checkApp(pkg: android.content.pm.PackageInfo): PermissionChanges? {
        val packageName = pkg.packageName
        val appName = pm.getApplicationLabel(pkg.applicationInfo!!).toString()
        val versionCode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            pkg.longVersionCode
        } else {
            @Suppress("DEPRECATION") pkg.versionCode.toLong()
        }
        val versionName = pkg.versionName ?: "—"
        val currentPerms = pkg.requestedPermissions?.toSet() ?: emptySet()

        val latest = snapshotDao.getLatest(packageName)

        // Yeni snapshot yaz (versiyon değiştiyse veya hiç snapshot yoksa)
        if (latest == null || latest.versionCode != versionCode) {
            snapshotDao.insert(
                AppPermissionSnapshotEntity(
                    packageName = packageName,
                    appName = appName,
                    versionCode = versionCode,
                    versionName = versionName,
                    permissions = currentPerms.joinToString(",")
                )
            )

            if (latest == null) return PermissionChanges(packageName, appName, versionName, emptyList(), emptyList(), isFirstInstall = true)

            val oldPerms = latest.permissionSet()
            val added   = (currentPerms - oldPerms).toList()
            val removed = (oldPerms - currentPerms).toList()

            if (added.isEmpty() && removed.isEmpty()) return null
            return PermissionChanges(packageName, appName, versionName, added, removed, isFirstInstall = false)
        }
        return null
    }

    private fun notifyChanges(changes: PermissionChanges) {
        if (changes.added.isNotEmpty()) {
            notificationHelper.sendPermissionAddedNotification(
                appName = changes.appName,
                versionName = changes.versionName,
                addedPerms = changes.added,
                removedPerms = changes.removed,
                packageName = changes.packageName
            )
        } else if (changes.removed.isNotEmpty()) {
            notificationHelper.sendPermissionRemovedNotification(
                appName = changes.appName,
                removedPerms = changes.removed
            )
        }
    }
}
