package io.privacydroid.util

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Kurulu uygulama bilgilerini PackageManager üzerinden çeker.
 * Root gerektirmez — API 1'den itibaren çalışır.
 */
@Singleton
class AppInfoHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {

    fun getAppName(packageName: String): String {
        return try {
            val appInfo = context.packageManager.getApplicationInfo(packageName, 0)
            context.packageManager.getApplicationLabel(appInfo).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            Timber.w("Uygulama bulunamadı: $packageName")
            packageName
        }
    }

    /** Uygulamanın sürüm adını döner ("3.1.2"); bulunamazsa "?" döner. */
    fun getVersionName(packageName: String): String {
        return try {
            context.packageManager.getPackageInfo(packageName, 0).versionName ?: "?"
        } catch (e: Exception) {
            "?"
        }
    }

    /** Uygulamanın Linux UID'sini döner; bulunamazsa -1. TrafficStats sorgusu için. */
    fun getUid(packageName: String): Int {
        return try {
            context.packageManager.getApplicationInfo(packageName, 0).uid
        } catch (e: Exception) {
            -1
        }
    }

    fun getAppIcon(packageName: String): Drawable? {
        return try {
            context.packageManager.getApplicationIcon(packageName)
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }
    }

    fun isSystemApp(packageName: String): Boolean {
        return try {
            val appInfo = context.packageManager.getApplicationInfo(packageName, 0)
            (appInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    fun getAllInstalledPackages(): List<String> {
        return context.packageManager
            .getInstalledApplications(PackageManager.GET_META_DATA)
            .map { it.packageName }
    }
}
