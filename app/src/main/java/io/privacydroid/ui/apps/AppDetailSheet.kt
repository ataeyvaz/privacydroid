package io.privacydroid.ui.apps

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.privacydroid.domain.model.AppInfo
import io.privacydroid.domain.model.AppNetworkStats
import io.privacydroid.domain.model.AppRiskLevel
import io.privacydroid.domain.model.BackgroundBehavior
import io.privacydroid.domain.model.DetectedSdk
import io.privacydroid.domain.model.resolvePermissionDisplayContextual
import io.privacydroid.domain.model.toMbString
import io.privacydroid.ui.components.AppIconImage
import io.privacydroid.ui.theme.AlertOrange
import io.privacydroid.ui.theme.AlertRed
import io.privacydroid.ui.theme.DarkSurfaceVariant
import io.privacydroid.ui.theme.PrimaryGreen
import io.privacydroid.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppDetailSheet(
    app: AppInfo,
    permissionUsageCounts: Map<String, Int> = emptyMap(),
    blockingEnabled: Boolean = true,
    onSetBlockingEnabled: (Boolean) -> Unit = {},
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ---- Başlık ----
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AppIconImage(app.packageName, app.appName, modifier = Modifier.size(48.dp))
                Column {
                    Text(app.appName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(app.packageName, style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                    Text(
                        "${app.riskLevel.emoji} ${app.riskLevel.label}",
                        style = MaterialTheme.typography.labelMedium,
                        color = riskColor(app.riskLevel)
                    )
                }
            }

            // Risk sebepleri
            if (app.riskReasons.isNotEmpty()) {
                InfoSection("Neden şüpheli?") {
                    app.riskReasons.forEach { reason ->
                        Text("• $reason", style = MaterialTheme.typography.bodySmall, color = AlertOrange)
                    }
                }
            }

            // ---- Reklam engelleme toggle (uygulama bazlı istisna) ----
            AppBlockingToggle(
                appName = app.appName,
                enabled = blockingEnabled,
                onSetEnabled = onSetBlockingEnabled
            )

            // ---- İzinler ----
            InfoSection("Bu uygulama şu izinleri istiyor") {
                // distinct() — aynı izin birden fazla kez listelenmesin
                val uniquePerms = app.dangerousPermissions.distinct()

                if (uniquePerms.isEmpty()) {
                    Text("✅ Tehlikeli izin yok",
                        style = MaterialTheme.typography.bodySmall, color = PrimaryGreen)
                } else {
                    // Önce HIGH, sonra MEDIUM, sonra LOW
                    // Bağlam duyarlı risk hesabı — uygulama kategorisine göre risk değişir
                    val sorted = uniquePerms
                        .map { perm ->
                            perm to resolvePermissionDisplayContextual(
                                perm, app.packageName, app.appName
                            )
                        }
                        .sortedByDescending { (_, display) -> display.riskLevel.ordinal }

                    sorted.forEach { (perm, display) ->
                        // İzin türü kodu — PermissionLog'da permission_type olarak saklanıyor
                        val permKey = perm.substringAfterLast(".")
                        val usageCount = permissionUsageCounts[permKey]

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                            verticalArrangement = Arrangement.spacedBy(1.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    "${display.emoji} ${display.label}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onBackground,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    display.riskLevel.emoji,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                            // Bağlam notu — yalnızca risk yükseltilmişse görünür
                            display.contextNote?.let { note ->
                                Text(
                                    "   $note",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = when (display.riskLevel) {
                                        AppRiskLevel.HIGH   -> AlertRed.copy(alpha = 0.8f)
                                        AppRiskLevel.MEDIUM -> AlertOrange.copy(alpha = 0.8f)
                                        AppRiskLevel.LOW    -> TextSecondary
                                    }
                                )
                            }
                            // Kullanım önerisi — 30 günlük log verisine dayalı
                            PermissionUsageHint(usageCount = usageCount)
                        }
                    }

                    Spacer(Modifier.height(4.dp))
                    Text(
                        "🔴 Çok Tehlikeli  🟡 Dikkat  🟢 Normal",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                }
            }

            // ---- Arka plan davranışı ----
            InfoSection("Arka plan davranışı") {
                BgRow(app.backgroundBehavior.hasBootReceiver, "Cihaz açılışında otomatik başlıyor")
                BgRow(app.backgroundBehavior.hasWakeLock,    "Cihazı uyku moduna geçmesini engelleyebilir")
                BgRow(app.backgroundBehavior.hasDozeypass,   "Doze Mode'u bypass ediyor (pil tasarrufu devre dışı)")
                BgRow(app.backgroundBehavior.hasExactAlarm,  "Tam zamanlı alarm kurabilir")
                if (!app.backgroundBehavior.hasBootReceiver &&
                    !app.backgroundBehavior.hasWakeLock &&
                    !app.backgroundBehavior.hasDozeypass &&
                    !app.backgroundBehavior.hasExactAlarm) {
                    Text("✅ Normal arka plan davranışı", style = MaterialTheme.typography.bodySmall, color = PrimaryGreen)
                }
            }

            // ---- Ağ istatistikleri ----
            if (app.networkStats.totalBytes24h > 0) {
                InfoSection("Ağ Kullanımı") {
                    NetworkStatsRows(app.networkStats)
                }
            }

            // ---- Tespit edilen SDK'lar ----
            if (app.detectedSdks.isNotEmpty()) {
                InfoSection("Gömülü Bileşenler") {
                    Text(
                        "Bu uygulamanın içinde bulunanlar:",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                    Spacer(Modifier.height(4.dp))
                    app.detectedSdks.forEach { sdk ->
                        SdkRow(sdk)
                    }
                }
            }

            // ---- Kurulum bilgileri ----
            val dateFmt = SimpleDateFormat("d MMMM yyyy", Locale("tr"))
            InfoSection("Uygulama Bilgileri") {
                InfoLine("Sürüm", app.versionName)
                InfoLine("Kurulum tarihi", dateFmt.format(Date(app.installTimeMs)))
                InfoLine("Son güncelleme", dateFmt.format(Date(app.lastUpdateMs)))
                InfoLine("İzin sayısı", "${app.dangerousPermissions.size} tehlikeli / ${app.requestedPermissions.size} toplam")
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Bileşenler
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Son 30 gündeki kullanım sayısına göre "güvenle iptal edilebilir mi?" önerisi.
 * [usageCount] null = hiç log yok (yeni kurulum veya izleme henüz başlamadı).
 */
@Composable
private fun PermissionUsageHint(usageCount: Int?) {
    val (text, color) = when {
        usageCount == null ->
            "   ❓ Henüz kullanım verisi yok — birkaç gün izleyin" to TextSecondary
        usageCount == 0 ->
            "   ✂️ Son 30 günde kullanılmadı — güvenle iptal edilebilir" to PrimaryGreen
        usageCount <= 5 ->
            "   ⚠️ Az kullanılıyor ($usageCount kez) — iptal etmeden önce test edin" to AlertOrange
        else ->
            "   🔒 Aktif kullanımda ($usageCount kez) — iptal etmek uygulamayı bozabilir" to AlertRed
    }
    Text(text, style = MaterialTheme.typography.labelSmall, color = color)
}

@Composable
private fun AppBlockingToggle(
    appName: String,
    enabled: Boolean,
    onSetEnabled: (Boolean) -> Unit
) {
    var showDisableDialog by remember { mutableStateOf(false) }

    if (showDisableDialog) {
        AlertDialog(
            onDismissRequest = { showDisableDialog = false },
            title = { Text("Engellemeyi kapat?") },
            text = {
                Text(
                    "$appName için reklam engelleme devre dışı bırakılacak. " +
                    "Bu uygulama tüm domainlere (reklam ve tracker dahil) erişebilecek.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(onClick = { onSetEnabled(false); showDisableDialog = false }) {
                    Text("Devre Dışı Bırak", color = AlertRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDisableDialog = false }) {
                    Text("Vazgeç", color = PrimaryGreen)
                }
            },
            containerColor = DarkSurfaceVariant
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(DarkSurfaceVariant, RoundedCornerShape(12.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("🛡️", style = MaterialTheme.typography.titleMedium)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "Reklam Engelleme",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                if (enabled) "Bu uygulama için engelleme aktif"
                else "🔓 Bu uygulama için engelleme devre dışı",
                style = MaterialTheme.typography.labelMedium,
                color = if (enabled) PrimaryGreen else TextSecondary
            )
        }
        Switch(
            checked = enabled,
            onCheckedChange = { wantEnabled ->
                if (wantEnabled) onSetEnabled(true) else showDisableDialog = true
            },
            colors = SwitchDefaults.colors(checkedThumbColor = PrimaryGreen)
        )
    }
}

@Composable
private fun InfoSection(title: String, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(DarkSurfaceVariant, RoundedCornerShape(12.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground)
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
        content()
    }
}

@Composable
private fun BgRow(active: Boolean, label: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(if (active) "⚠️" else "✅", style = MaterialTheme.typography.bodySmall)
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = if (active) AlertOrange else PrimaryGreen
        )
    }
}

@Composable
private fun SdkRow(sdk: DetectedSdk) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "${sdk.riskLevel.emoji} ${sdk.name}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Medium
            )
            Text(sdk.description, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
        }
    }
}

@Composable
private fun NetworkStatsRows(stats: AppNetworkStats) {
    InfoLine("Son 24 saat gönderilen", "↑ ${stats.txBytes24h.toMbString()}")
    InfoLine("Son 24 saat alınan", "↓ ${stats.rxBytes24h.toMbString()}")
    InfoLine("Son 7 gün toplam", "${stats.totalBytes7d.toMbString()}")
    if (stats.isAnomaly) {
        Text("⚠️ ${stats.anomalyReason}", style = MaterialTheme.typography.labelSmall, color = AlertRed)
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        Text(value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Medium)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Yardımcılar
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun riskColor(level: AppRiskLevel) = when (level) {
    AppRiskLevel.LOW    -> PrimaryGreen
    AppRiskLevel.MEDIUM -> AlertOrange
    AppRiskLevel.HIGH   -> AlertRed
}

