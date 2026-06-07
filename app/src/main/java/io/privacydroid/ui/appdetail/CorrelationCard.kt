package io.privacydroid.ui.appdetail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Camera
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.SdStorage
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.privacydroid.data.local.entity.CorrelationResultEntity
import io.privacydroid.domain.model.SuspicionLevel
import io.privacydroid.ui.theme.AlertOrange
import io.privacydroid.ui.theme.AlertRed
import io.privacydroid.ui.theme.DarkSurfaceVariant
import io.privacydroid.ui.theme.PrimaryGreen
import io.privacydroid.ui.theme.TextSecondary

/**
 * Kamera/mikrofon erişiminin ağ trafiği ve medya dosyası korelasyonunu gösterir.
 *
 * Örnek görünüm:
 *   📷 Kamera — son erişim
 *   ↑ 2.3 MB gönderildi
 *   💾 Yeni dosya: Hayır
 *   ⚠️ Şüphe: YÜKSEK
 */
@Composable
fun CorrelationCard(
    correlation: CorrelationResultEntity,
    modifier: Modifier = Modifier
) {
    val suspicion = SuspicionLevel.fromString(correlation.suspicionLevel)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(DarkSurfaceVariant, RoundedCornerShape(12.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Başlık
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = if (correlation.accessType == "CAMERA")
                    Icons.Outlined.Camera else Icons.Outlined.Mic,
                contentDescription = null,
                tint = if (correlation.accessType == "CAMERA")
                    io.privacydroid.ui.theme.ColorCamera
                else io.privacydroid.ui.theme.ColorMicrophone,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = if (correlation.accessType == "CAMERA") "Kamera" else "Mikrofon",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "— son erişim korelasyonu",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

        // Ağ trafiği
        CorrelationRow(
            icon = { Icon(Icons.Outlined.Upload, null, tint = networkColor(correlation.networkBytesSent), modifier = Modifier.size(16.dp)) },
            label = "Gönderilen veri",
            value = formatBytes(correlation.networkBytesSent),
            valueColor = networkColor(correlation.networkBytesSent)
        )

        // Medya dosyası
        CorrelationRow(
            icon = { Icon(Icons.Outlined.SdStorage, null, tint = TextSecondary, modifier = Modifier.size(16.dp)) },
            label = "Yeni medya dosyası",
            value = if (correlation.newMediaCreated) {
                val kb = (correlation.mediaFileSizeBytes ?: 0L) / 1024
                "Evet — ${kb}KB"
            } else "Hayır",
            valueColor = if (correlation.newMediaCreated) PrimaryGreen else MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Medya yolu (varsa)
        correlation.mediaFilePath?.let { path ->
            val shortPath = path.substringAfterLast("/")
            Text(
                text = "  $shortPath",
                style = MaterialTheme.typography.labelMedium,
                color = TextSecondary,
                modifier = Modifier.padding(start = 24.dp)
            )
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

        // Şüphe seviyesi
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                Icons.Outlined.Warning,
                null,
                tint = suspicionColor(suspicion),
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = "Şüphe Seviyesi",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
            Text(
                text = suspicion.shortLabel,
                style = MaterialTheme.typography.labelMedium,
                color = suspicionColor(suspicion),
                fontWeight = FontWeight.Bold
            )
            if (suspicion == SuspicionLevel.HIGH) {
                Text(
                    text = suspicionExplanation(correlation),
                    style = MaterialTheme.typography.labelSmall,
                    color = suspicionColor(suspicion).copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
private fun CorrelationRow(
    icon: @Composable () -> Unit,
    label: String,
    value: String,
    valueColor: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            icon()
            Text(label, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        }
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            color = valueColor,
            fontWeight = FontWeight.Medium
        )
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes <= 0L -> "Yok"
    bytes < 1_024L -> "$bytes B"
    bytes < 1_024L * 1024 -> "${"%.1f".format(bytes / 1024.0)} KB"
    else -> "${"%.1f".format(bytes / 1_048_576.0)} MB"
}

@Composable
private fun networkColor(bytes: Long): Color = when {
    bytes <= 0L -> TextSecondary
    bytes > 1_000_000L -> AlertRed
    bytes > 100_000L -> AlertOrange
    else -> PrimaryGreen
}

@Composable
private fun suspicionColor(level: SuspicionLevel): Color = when (level) {
    SuspicionLevel.LOW -> PrimaryGreen
    SuspicionLevel.MEDIUM -> AlertOrange
    SuspicionLevel.HIGH -> AlertRed
}

private fun suspicionExplanation(c: CorrelationResultEntity): String = when {
    c.networkBytesSent > 1_000_000L && !c.newMediaCreated ->
        "(dosyasız büyük veri gönderimi)"
    c.isBackground -> "(gece arka plan trafiği)"
    else -> ""
}
