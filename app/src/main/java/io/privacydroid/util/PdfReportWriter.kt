package io.privacydroid.util

import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument

/**
 * Metin tabanlı raporu A4 PDF'e dönüştürür. Üçüncü parti kütüphane yok —
 * yalnızca android.graphics.pdf.PdfDocument kullanılır (root gerektirmez).
 *
 * Uzun raporlar otomatik olarak birden fazla sayfaya bölünür. İlk sayfada
 * başlık + alt başlık (cihaz/tarih) yer alır.
 */
object PdfReportWriter {

    // A4 @72dpi noktası (point): 595 x 842
    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private const val MARGIN = 40f
    private const val LINE_HEIGHT = 16f
    private const val TITLE_SIZE = 18f
    private const val SUBTITLE_SIZE = 11f
    private const val BODY_SIZE = 10f

    /**
     * [title] başlık sayfasında büyük gösterilir. [subtitle] başlığın altında küçük
     * (ör. "PrivacyDroid · 7 Haziran 2026 · Xiaomi"). [body] tüm rapor metni.
     *
     * Dönen PdfDocument çağıran tarafından writeTo() sonrası close() edilmelidir.
     */
    fun build(title: String, subtitle: String, body: String): PdfDocument {
        val doc = PdfDocument()

        val titlePaint = Paint().apply {
            color = Color.rgb(0, 150, 70)
            textSize = TITLE_SIZE
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        val subtitlePaint = Paint().apply {
            color = Color.DKGRAY
            textSize = SUBTITLE_SIZE
            isAntiAlias = true
        }
        val bodyPaint = Paint().apply {
            color = Color.BLACK
            textSize = BODY_SIZE
            typeface = Typeface.MONOSPACE
            isAntiAlias = true
        }

        // Satırları sayfa genişliğine göre kır.
        val maxLineChars = ((PAGE_WIDTH - 2 * MARGIN) / (BODY_SIZE * 0.6f)).toInt()
        val lines = body.lines().flatMap { wrap(it, maxLineChars) }

        var pageNumber = 1
        var lineIndex = 0
        while (lineIndex < lines.size || pageNumber == 1) {
            val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
            val page = doc.startPage(pageInfo)
            val canvas = page.canvas
            var y = MARGIN

            if (pageNumber == 1) {
                canvas.drawText(title, MARGIN, y + TITLE_SIZE, titlePaint)
                y += TITLE_SIZE + 10f
                canvas.drawText(subtitle, MARGIN, y + SUBTITLE_SIZE, subtitlePaint)
                y += SUBTITLE_SIZE + 18f
            }

            while (lineIndex < lines.size && y < PAGE_HEIGHT - MARGIN) {
                canvas.drawText(lines[lineIndex], MARGIN, y + BODY_SIZE, bodyPaint)
                y += LINE_HEIGHT
                lineIndex++
            }

            doc.finishPage(page)
            pageNumber++
            if (lineIndex >= lines.size) break
        }

        return doc
    }

    /** Bir satırı [maxChars] uzunluğunda parçalara böler (kelime sınırını korur). */
    private fun wrap(line: String, maxChars: Int): List<String> {
        if (line.length <= maxChars || maxChars <= 0) return listOf(line)
        val result = mutableListOf<String>()
        var remaining = line
        while (remaining.length > maxChars) {
            var cut = remaining.lastIndexOf(' ', maxChars)
            if (cut <= 0) cut = maxChars
            result.add(remaining.substring(0, cut))
            remaining = remaining.substring(cut).trimStart()
        }
        if (remaining.isNotEmpty()) result.add(remaining)
        return result
    }
}
