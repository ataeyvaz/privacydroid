package io.privacydroid.util

import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

data class ParsedCrashSummary(
    val timestamp: String,      // "5 Haziran 2026, 10:24"
    val userMessage: String,    // "Liste ekranı açılırken hata oluştu"
    val technicalCode: String,  // "LazyColumn-DuplicateKey"
    val suggestion: String      // "Uygulamayı kapatıp tekrar açın"
)

/**
 * Ham crash log içeriğini kullanıcı dostu özete çevirir.
 * Hata türü tespiti içerik taramasıyla yapılır.
 */
object CrashLogParser {

    private val inputFmt = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault())
        .also { it.timeZone = TimeZone.getDefault() }
    private val outputFmt = SimpleDateFormat("d MMMM yyyy, HH:mm", Locale("tr"))
        .also { it.timeZone = TimeZone.getDefault() }

    fun parse(fileName: String, content: String): ParsedCrashSummary {
        val timestamp = parseTimestamp(fileName)
        val (message, code, suggestion) = classify(content)
        return ParsedCrashSummary(timestamp, message, code, suggestion)
    }

    private fun parseTimestamp(fileName: String): String {
        // "crash_2026-06-05_10-24-11.txt" → "5 Haziran 2026, 10:24"
        return try {
            val raw = fileName.removePrefix("crash_").removeSuffix(".txt")
            val date = inputFmt.parse(raw)
            date?.let { outputFmt.format(it) } ?: raw
        } catch (_: Exception) {
            fileName.removePrefix("crash_").removeSuffix(".txt")
        }
    }

    private fun classify(content: String): Triple<String, String, String> {
        return when {
            // Lazy list duplicate key
            ("LazyColumn" in content || "LazyRow" in content) &&
                    ("was already used" in content || "DuplicateKey" in content || "duplicate" in content.lowercase()) ->
                Triple(
                    "Liste ekranı açılırken hata oluştu",
                    "LazyColumn-DuplicateKey",
                    "Uygulamayı kapatıp tekrar açın"
                )

            "NullPointerException" in content ->
                Triple(
                    "Veri yüklenirken hata oluştu",
                    "NullPointerException",
                    "Uygulamayı kapatıp tekrar açın"
                )

            "SecurityException" in content ->
                Triple(
                    "İzin hatası oluştu",
                    "SecurityException",
                    "Ayarlar ekranından uygulama izinlerini kontrol edin"
                )

            "OutOfMemoryError" in content ->
                Triple(
                    "Bellek yetersiz",
                    "OutOfMemoryError",
                    "Telefonu yeniden başlatın, ardından uygulamayı açın"
                )

            "NetworkOnMainThreadException" in content ->
                Triple(
                    "Ağ bağlantı hatası oluştu",
                    "NetworkOnMainThread",
                    "Uygulamayı kapatıp tekrar açın"
                )

            "IllegalStateException" in content ->
                Triple(
                    "Uygulama beklenmedik bir durumla karşılaştı",
                    "IllegalStateException",
                    "Uygulamayı kapatıp tekrar açın"
                )

            "IllegalArgumentException" in content ->
                Triple(
                    "Veri işlenirken hata oluştu",
                    "IllegalArgumentException",
                    "Uygulamayı kapatıp tekrar açın"
                )

            "SQLiteException" in content || "RoomException" in content ->
                Triple(
                    "Veritabanı hatası oluştu",
                    "DatabaseError",
                    "Uygulamayı kapatıp tekrar açın; sorun devam ederse yeniden yükleyin"
                )

            else -> {
                // Stack trace'den exception sınıf adını bulmaya çalış
                val exceptionCode = content.lines()
                    .firstOrNull { line ->
                        Regex("[A-Z][a-zA-Z]+(Exception|Error)").containsMatchIn(line)
                    }
                    ?.let { line ->
                        Regex("[A-Z][a-zA-Z]+(Exception|Error)").find(line)?.value
                    } ?: "UnknownError"

                Triple(
                    "Beklenmedik hata oluştu",
                    exceptionCode,
                    "Uygulamayı kapatıp tekrar açın"
                )
            }
        }
    }
}
