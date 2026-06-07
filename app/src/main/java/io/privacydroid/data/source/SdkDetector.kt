package io.privacydroid.data.source

import android.content.Context
import android.content.pm.PackageManager
import dagger.hilt.android.qualifiers.ApplicationContext
import io.privacydroid.domain.model.DetectedSdk
import io.privacydroid.domain.model.KNOWN_SDK_SIGNATURES
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * APK içindeki gömülü SDK ve izleme kütüphanelerini tespit eder.
 *
 * Tespit yöntemleri:
 *   1. ApplicationInfo.metaData key'leri → bilinen SDK anahtarlarıyla eşleşme
 *   2. PackageInfo.services/receivers/providers class adları → paket adı prefix eşleşmesi
 *   3. İstenen izinler → bilinen SDK pattern'leri
 *
 * PackageManager.GET_SERVICES vb. ekstra flag'ler getPackageInfo'yu yavaşlatır,
 * bu yüzden yalnızca detay görünümünde (tıklayınca) çağrılır.
 */
@Singleton
class SdkDetector @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val pm = context.packageManager

    /**
     * Temel SDK tespiti — metaData üzerinden. Hızlı, liste görünümü için.
     */
    fun detectFromMetaData(packageName: String): List<DetectedSdk> {
        return try {
            val appInfo = pm.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
            val metaBundle = appInfo.metaData ?: return emptyList()

            val detected = mutableListOf<DetectedSdk>()
            val metaKeys = metaBundle.keySet().joinToString("|").lowercase()

            for ((signature, sdk) in KNOWN_SDK_SIGNATURES) {
                if (signature.lowercase() in metaKeys) {
                    detected.add(sdk)
                }
            }
            detected.distinctBy { it.detectionKey }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Derin SDK tespiti — component sınıf adları üzerinden.
     * Daha kapsamlı ama yavaş — yalnızca detay görünümünde çağrılır.
     */
    fun detectDeep(packageName: String): List<DetectedSdk> {
        val basic = detectFromMetaData(packageName)
        val fromComponents = detectFromComponents(packageName)
        return (basic + fromComponents).distinctBy { it.detectionKey }
    }

    private fun detectFromComponents(packageName: String): List<DetectedSdk> {
        return try {
            val flags = PackageManager.GET_SERVICES or
                    PackageManager.GET_RECEIVERS or
                    PackageManager.GET_PROVIDERS or
                    PackageManager.GET_ACTIVITIES

            val pkgInfo = pm.getPackageInfo(packageName, flags)
            val classNames = buildList {
                pkgInfo.services?.forEach { add(it.name) }
                pkgInfo.receivers?.forEach { add(it.name) }
                pkgInfo.providers?.forEach { add(it.name) }
                pkgInfo.activities?.forEach { add(it.name) }
            }

            val allClasses = classNames.joinToString("|").lowercase()
            val detected = mutableListOf<DetectedSdk>()

            for ((signature, sdk) in KNOWN_SDK_SIGNATURES) {
                if (signature.lowercase() in allClasses) {
                    detected.add(sdk)
                }
            }
            detected
        } catch (e: Exception) {
            Timber.w("SDK derin tarama hatası [$packageName]: ${e.message}")
            emptyList()
        }
    }
}
