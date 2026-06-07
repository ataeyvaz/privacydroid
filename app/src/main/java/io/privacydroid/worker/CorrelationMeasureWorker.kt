package io.privacydroid.worker

import android.content.Context
import android.net.TrafficStats
import android.os.Build
import android.provider.MediaStore
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import io.privacydroid.data.local.dao.CorrelationResultDao
import io.privacydroid.data.local.entity.CorrelationResultEntity
import io.privacydroid.data.repository.CrashLogRepository
import io.privacydroid.domain.model.SuspicionLevel
import timber.log.Timber
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * Erişim tespitinden [INPUT_DELAY_SECONDS] saniye sonra çalışır.
 *
 * Ölçümler:
 *   - TrafficStats delta: erişimden bu yana gönderilen byte
 *   - MediaStore: son 5 dakikada oluşturulan fotoğraf/video var mı?
 *
 * Hata izolasyonu: herhangi bir hata sessizce loglanır → Result.failure() (retry yok).
 * Korelasyon verisi zamana bağlıdır; tekrar çalışsa da anlamsız olur.
 */
@HiltWorker
class CorrelationMeasureWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
    private val correlationDao: CorrelationResultDao,
    private val crashLogRepository: CrashLogRepository
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val KEY_PACKAGE     = "pkg"
        const val KEY_APP_NAME    = "app_name"
        const val KEY_UID         = "uid"
        const val KEY_BASELINE    = "baseline_bytes"
        const val KEY_ACCESS_TYPE = "access_type"
        const val KEY_ACCESS_MS   = "access_ms"
        const val KEY_IS_BG       = "is_bg"

        private const val MEDIA_LOOKBACK_MS = 5 * 60 * 1000L  // 5 dakika
    }

    override suspend fun doWork(): Result {
        return try {
            measure()
            Result.success()
        } catch (e: Exception) {
            crashLogRepository.writeCrashLog(Thread.currentThread(), e)
            Timber.e(e, "CorrelationMeasureWorker beklenmedik hata")
            Result.failure()  // retry yok — zaman bağımlı
        }
    }

    private suspend fun measure() {
        val packageName = inputData.getString(KEY_PACKAGE) ?: return
        val appName     = inputData.getString(KEY_APP_NAME) ?: packageName
        val uid         = inputData.getInt(KEY_UID, -1)
        val baseline    = inputData.getLong(KEY_BASELINE, 0L)
        val accessType  = inputData.getString(KEY_ACCESS_TYPE) ?: return
        val accessMs    = inputData.getLong(KEY_ACCESS_MS, 0L)
        val isBackground = inputData.getBoolean(KEY_IS_BG, false)

        // ---- 1. Ağ trafiği delta ----
        val currentBytes = TrafficStats.getUidTxBytes(uid)
        val networkDelta = when {
            currentBytes == TrafficStats.UNSUPPORTED.toLong() -> 0L
            currentBytes < baseline -> 0L  // Sayaç sıfırlanmış (reboot) — nadir
            else -> currentBytes - baseline
        }

        // ---- 2. MediaStore sorgusu ----
        val mediaResult = queryNewMedia()

        // ---- 3. Şüphe seviyesi ----
        val cal = Calendar.getInstance().apply { timeInMillis = accessMs }
        val isNight = cal.get(Calendar.HOUR_OF_DAY) < 6

        val suspicion = SuspicionLevel.calculate(
            isBackground     = isBackground,
            isNightTime      = isNight,
            networkBytesSent = networkDelta,
            newMediaCreated  = mediaResult.found,
            accessType       = accessType
        )

        // ---- 4. Room'a kaydet ----
        correlationDao.insert(
            CorrelationResultEntity(
                packageName       = packageName,
                appName           = appName,
                accessType        = accessType,
                accessStartMs     = accessMs,
                accessDurationMs  = 0L,  // UsageStats per-access süreyi vermiyor
                isBackground      = isBackground,
                networkBytesSent  = networkDelta,
                newMediaCreated   = mediaResult.found,
                mediaFilePath     = mediaResult.path,
                mediaFileSizeBytes = mediaResult.sizeBytes,
                suspicionLevel    = suspicion.name
            )
        )

        Timber.d(
            "Korelasyon kaydedildi [$packageName]: " +
                    "tür=$accessType, ağ=${networkDelta}B, " +
                    "medya=${mediaResult.found}, şüphe=${suspicion.name}"
        )
    }

    /**
     * Son [MEDIA_LOOKBACK_MS] ms içinde MediaStore'a eklenen ilk fotoğraf veya videoyu döner.
     * İzin yoksa veya sorgu başarısız olursa [MediaQueryResult.empty] döner — uygulama devam eder.
     */
    private fun queryNewMedia(): MediaQueryResult {
        val sinceTimeSec = (System.currentTimeMillis() - MEDIA_LOOKBACK_MS) / 1000

        val uriList = buildList {
            add(MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                add(MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
            }
        }

        for (uri in uriList) {
            try {
                val cursor = context.contentResolver.query(
                    uri,
                    arrayOf(
                        MediaStore.MediaColumns.DATA,
                        MediaStore.MediaColumns.SIZE
                    ),
                    "${MediaStore.MediaColumns.DATE_ADDED} >= ?",
                    arrayOf(sinceTimeSec.toString()),
                    "${MediaStore.MediaColumns.DATE_ADDED} DESC"
                ) ?: continue

                cursor.use { c ->
                    if (c.moveToFirst()) {
                        val path  = c.getString(0)
                        val bytes = c.getLong(1)
                        return MediaQueryResult(found = true, path = path, sizeBytes = bytes)
                    }
                }
            } catch (e: SecurityException) {
                // READ_MEDIA_IMAGES / READ_EXTERNAL_STORAGE izni yok — sessizce geç
                Timber.d("MediaStore erişim izni yok: ${e.message}")
            } catch (e: Exception) {
                Timber.w("MediaStore sorgu hatası: ${e.message}")
            }
        }

        return MediaQueryResult.empty
    }

    private data class MediaQueryResult(
        val found: Boolean,
        val path: String?,
        val sizeBytes: Long?
    ) {
        companion object {
            val empty = MediaQueryResult(false, null, null)
        }
    }
}
