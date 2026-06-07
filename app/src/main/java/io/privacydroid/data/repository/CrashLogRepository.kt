package io.privacydroid.data.repository

import android.content.Context
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import io.privacydroid.BuildConfig
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Crash log dosyalarını yönetir.
 *
 * Konum: /data/data/io.privacydroid/files/crash_logs/
 * Format: crash_yyyy-MM-dd_HH-mm-ss.txt
 * Kural: Son [MAX_CRASH_LOGS] dosya tutulur, eskiler silinir.
 */
@Singleton
class CrashLogRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        private const val CRASH_LOG_DIR = "crash_logs"
        const val MAX_CRASH_LOGS = 10
        private val FILE_TIMESTAMP_FMT = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault())
            .also { it.timeZone = TimeZone.getDefault() }
        private val REPORT_TIMESTAMP_FMT = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            .also { it.timeZone = TimeZone.getDefault() }
    }

    val crashLogDir: File
        get() = File(context.filesDir, CRASH_LOG_DIR).also { it.mkdirs() }

    /** En yeniden eskiye sıralı crash log dosyaları. */
    fun listCrashLogs(): List<File> =
        crashLogDir.listFiles()
            ?.filter { it.name.startsWith("crash_") && it.name.endsWith(".txt") }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()

    fun countCrashLogs(): Int = listCrashLogs().size

    fun readCrashLog(file: File): String = runCatching { file.readText() }.getOrDefault("")

    fun readLatestCrashLog(): String? = listCrashLogs().firstOrNull()?.readText()

    fun deleteAllCrashLogs(): Int {
        val files = listCrashLogs()
        files.forEach { it.delete() }
        return files.size
    }

    /**
     * Bir crash'in thread ve throwable bilgisini dosyaya yazar.
     * Yazma başarısız olursa sessizce geçer — crash akışını engellememeli.
     */
    fun writeCrashLog(thread: Thread, throwable: Throwable) {
        runCatching {
            val timestamp = FILE_TIMESTAMP_FMT.format(Date())
            val file = File(crashLogDir, "crash_$timestamp.txt")
            file.writeText(buildReport(thread, throwable))
            pruneOldLogs()
        }
    }

    /**
     * Fatal olmayan bir hatayı (ör. bir worker'ın yakaladığı exception) tam stack
     * trace ile dosyaya yazar. Uygulamayı çökertmeyen ama teşhis için tutulması
     * gereken hatalar içindir.
     *
     * [tag] dosya adına ve raporun başına eklenir — hangi bileşenin hata verdiğini
     * ayırt etmek için (ör. "tracker_scan").
     */
    fun writeNonFatal(tag: String, throwable: Throwable) {
        runCatching {
            val timestamp = FILE_TIMESTAMP_FMT.format(Date())
            val safeTag = tag.replace(Regex("[^a-zA-Z0-9_-]"), "_")
            val file = File(crashLogDir, "crash_${timestamp}_$safeTag.txt")
            file.writeText(buildNonFatalReport(tag, throwable))
            pruneOldLogs()
        }
    }

    private fun pruneOldLogs() {
        val files = crashLogDir.listFiles()
            ?.filter { it.name.startsWith("crash_") }
            ?.sortedBy { it.lastModified() }
            ?: return
        if (files.size > MAX_CRASH_LOGS) {
            files.take(files.size - MAX_CRASH_LOGS).forEach { it.delete() }
        }
    }

    private fun buildNonFatalReport(tag: String, throwable: Throwable): String = buildString {
        appendLine("=== PrivacyDroid Non-Fatal Report ===")
        appendLine("Tag     : $tag")
        appendLine("Time    : ${REPORT_TIMESTAMP_FMT.format(Date())}")
        appendLine("Version : ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        appendLine("Android : ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        appendLine("Device  : ${Build.MANUFACTURER} ${Build.MODEL}")
        appendLine("Thread  : ${Thread.currentThread().name}")
        appendLine()
        appendLine("--- Exception ---")
        appendLine(throwable.stackTraceToString())

        // Cause zinciri (max 5 derinlik)
        var cause = throwable.cause
        var depth = 0
        while (cause != null && depth < 5) {
            appendLine()
            appendLine("--- Caused by [depth=$depth] ---")
            appendLine(cause.stackTraceToString())
            cause = cause.cause
            depth++
        }
    }

    private fun buildReport(thread: Thread, throwable: Throwable): String = buildString {
        appendLine("=== PrivacyDroid Crash Report ===")
        appendLine("Time    : ${REPORT_TIMESTAMP_FMT.format(Date())}")
        appendLine("Version : ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        appendLine("Android : ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        appendLine("Device  : ${Build.MANUFACTURER} ${Build.MODEL}")
        appendLine("Thread  : ${thread.name} (id=${thread.id})")
        appendLine()
        appendLine("--- Exception ---")
        appendLine(throwable.stackTraceToString())

        // Cause zinciri (max 5 derinlik)
        var cause = throwable.cause
        var depth = 0
        while (cause != null && depth < 5) {
            appendLine()
            appendLine("--- Caused by [depth=$depth] ---")
            appendLine(cause.stackTraceToString())
            cause = cause.cause
            depth++
        }
    }
}
