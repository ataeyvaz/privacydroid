package io.privacydroid.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import timber.log.Timber
import java.io.File

/**
 * Rapor paylaşımı ve dosya export'u için yardımcılar.
 *
 * PDF, Android'in yerleşik MediaStore API'si (API 29+) ile
 * /storage/emulated/0/Documents/PrivacyDroid/ altına kaydedilir. Eski sürümlerde
 * (API 26–28) uygulamaya özel Documents dizinine yazılır ve FileProvider ile
 * paylaşılır — runtime depolama izni gerektirmez.
 */
object ReportExporter {

    private const val SUBDIR = "PrivacyDroid"

    /** Düz metin raporunu Android Share Sheet ile paylaşır. */
    fun shareText(context: Context, text: String, subject: String) {
        val intent = Intent.createChooser(
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
                putExtra(Intent.EXTRA_SUBJECT, subject)
            },
            "Raporu Paylaş"
        ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        context.startActivity(intent)
    }

    /** Metni panoya kopyalar. */
    fun copyToClipboard(context: Context, label: String, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
    }

    /**
     * Raporu PDF olarak kaydeder. Başarılıysa paylaşılabilir/açılabilir Uri döner,
     * hata olursa null.
     */
    fun savePdf(
        context: Context,
        fileName: String,
        title: String,
        subtitle: String,
        body: String
    ): Uri? {
        val document = PdfReportWriter.build(title, subtitle, body)
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                savePdfViaMediaStore(context, fileName, document)
            } else {
                savePdfToAppDir(context, fileName, document)
            }
        } catch (e: Exception) {
            Timber.e(e, "PDF kaydedilemedi")
            null
        } finally {
            document.close()
        }
    }

    private fun savePdfViaMediaStore(
        context: Context,
        fileName: String,
        document: android.graphics.pdf.PdfDocument
    ): Uri? {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
            put(
                MediaStore.MediaColumns.RELATIVE_PATH,
                Environment.DIRECTORY_DOCUMENTS + "/" + SUBDIR
            )
        }
        val collection = MediaStore.Files.getContentUri("external")
        val uri = resolver.insert(collection, values) ?: return null
        resolver.openOutputStream(uri)?.use { out -> document.writeTo(out) }
        return uri
    }

    private fun savePdfToAppDir(
        context: Context,
        fileName: String,
        document: android.graphics.pdf.PdfDocument
    ): Uri {
        val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), SUBDIR)
        if (!dir.exists()) dir.mkdirs()
        val file = File(dir, fileName)
        file.outputStream().use { out -> document.writeTo(out) }
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    /** Kaydedilen PDF'i Share Sheet ile paylaşır. */
    fun sharePdf(context: Context, uri: Uri) {
        val intent = Intent.createChooser(
            Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            },
            "PDF Raporu Paylaş"
        ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        context.startActivity(intent)
    }

    /** PDF'i "Dosyalarda Aç" — bir PDF görüntüleyici ile açar. */
    fun openPdf(context: Context, uri: Uri) {
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/pdf")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Timber.w("PDF açacak uygulama bulunamadı: ${e.message}")
        }
    }
}
