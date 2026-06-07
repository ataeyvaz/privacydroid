package io.privacydroid.util

import io.privacydroid.data.repository.CrashLogRepository
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Yakalanmamış exception'ları dosyaya yazar.
 *
 * [install] çağrıldıktan sonra herhangi bir thread'de UncaughtException oluşursa:
 *   1. Stack trace crash_logs/ dizinine yazılır.
 *   2. Default handler'a iletilir (sistem crash mekanizması devam eder).
 *
 * Thread safety: Thread.setDefaultUncaughtExceptionHandler thread-safe'dir.
 */
@Singleton
class CrashLogger @Inject constructor(
    private val crashLogRepository: CrashLogRepository
) : Thread.UncaughtExceptionHandler {

    private var defaultHandler: Thread.UncaughtExceptionHandler? = null

    /**
     * Default UncaughtExceptionHandler'ın yerine kur.
     * Application.onCreate'de bir kez çağrılmalı.
     */
    fun install() {
        defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler(this)
        Timber.d("CrashLogger kuruldu — crash logları: ${crashLogRepository.crashLogDir.absolutePath}")
    }

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        try {
            crashLogRepository.writeCrashLog(thread, throwable)
            Timber.e(throwable, "Uygulama çöktü — log dosyaya yazıldı [thread=${thread.name}]")
        } catch (_: Exception) {
            // CrashLogger'ın kendisi başarısız olursa uygulamayı bloklama
        }

        // Sistem default handler'ına ilet — process'i sonlandırır, ANR raporlaması devam eder
        defaultHandler?.uncaughtException(thread, throwable)
    }
}
