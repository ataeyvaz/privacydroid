package io.privacydroid.ui.settings

import android.app.Activity
import android.net.VpnService
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.BatteryFull
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.WifiFind
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.privacydroid.data.model.BlockingMode
import io.privacydroid.data.model.MonitoringMode
import io.privacydroid.data.model.NotificationSensitivity
import io.privacydroid.ui.theme.AlertRed
import io.privacydroid.ui.theme.AlertYellow
import io.privacydroid.ui.theme.DarkSurfaceVariant
import io.privacydroid.ui.theme.PrimaryGreen
import io.privacydroid.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val deviceReport by viewModel.deviceReport.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Crash log iki katmanlı dialogu
    uiState.selectedEntry?.let { entry ->
        CrashLogDialog(
            entry = entry,
            showTechnicalDetail = uiState.showTechnicalDetail,
            onToggleTechnical = viewModel::toggleTechnicalDetail,
            onDismiss = viewModel::dismissCrashLogView
        )
    }

    // Faz 3 Modül 4: cihaz geneli rapor dialogu
    deviceReport.text?.let { reportText ->
        DeviceReportDialog(
            text = reportText,
            onShare = viewModel::shareDeviceReport,
            onCopy = {
                viewModel.copyDeviceReport()
                android.widget.Toast.makeText(context, "Rapor panoya kopyalandı", android.widget.Toast.LENGTH_SHORT).show()
            },
            onSavePdf = {
                val uri = viewModel.saveDeviceReportPdf()
                if (uri != null) {
                    android.widget.Toast.makeText(context, "PDF kaydedildi", android.widget.Toast.LENGTH_LONG).show()
                    io.privacydroid.util.ReportExporter.openPdf(context, uri)
                } else {
                    android.widget.Toast.makeText(context, "PDF kaydedilemedi", android.widget.Toast.LENGTH_SHORT).show()
                }
            },
            onDismiss = viewModel::dismissDeviceReport
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ayarlar") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Geri")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            SectionHeader("İzleme Modu")
            MonitoringModeCard(
                currentMode = uiState.monitoringMode,
                onModeChange = viewModel::setMonitoringMode
            )

            Spacer(modifier = Modifier.height(8.dp))
            SectionHeader("Bildirimler")

            ToggleRow(
                icon = { Icon(Icons.Outlined.Notifications, null, tint = PrimaryGreen) },
                title = "Şüpheli Aktivite Bildirimi",
                description = "Arka plan sensör erişimi veya gece aktivitesi tespit edilince",
                checked = uiState.suspiciousNotificationsEnabled,
                onCheckedChange = viewModel::setSuspiciousNotificationsEnabled
            )
            ToggleRow(
                icon = {
                    Icon(
                        Icons.Outlined.Notifications,
                        null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                title = "Günlük Özet",
                description = "Her sabah bugünkü izin aktivitesi özeti",
                checked = uiState.summaryNotificationsEnabled,
                onCheckedChange = viewModel::setSummaryNotificationsEnabled
            )

            Spacer(modifier = Modifier.height(8.dp))
            SectionHeader("Bildirim Hassasiyeti")

            NotificationSensitivityCard(
                currentSensitivity = uiState.notificationSensitivity,
                onSensitivityChange = viewModel::setNotificationSensitivity
            )

            Spacer(modifier = Modifier.height(8.dp))
            SectionHeader("Tracker İzleme")

            TrackerSection(
                enabled = uiState.trackerMonitoringEnabled,
                lastScanLabel = uiState.lastTrackerScanLabel,
                scanState = uiState.scanState,
                scanMessage = uiState.scanMessage,
                onToggle = viewModel::setTrackerMonitoringEnabled,
                onManualScan = viewModel::triggerManualTrackerScan
            )

            Spacer(modifier = Modifier.height(8.dp))
            SectionHeader("Gelişmiş Tracker Tespiti")

            VpnModeSection(
                enabled = uiState.vpnModeEnabled,
                onRequestEnable = { viewModel.setVpnModeEnabled(true) },
                onDisable = { viewModel.setVpnModeEnabled(false) }
            )

            Spacer(modifier = Modifier.height(8.dp))
            SectionHeader("DNS Engelleme")

            DnsBlockingSection(
                mode = uiState.blockingMode,
                adsBlockedToday = uiState.adsBlockedToday,
                trackersBlockedToday = uiState.trackersBlockedToday,
                onModeChange = viewModel::setBlockingMode
            )

            Spacer(modifier = Modifier.height(8.dp))
            SectionHeader("Raporlar")

            DeviceReportSection(
                isGenerating = deviceReport.isGenerating,
                onGenerate = viewModel::generateDeviceReport
            )

            Spacer(modifier = Modifier.height(8.dp))
            SectionHeader("Hata Günlükleri")

            CrashLogSection(
                uiState = uiState,
                onViewLog = { entry -> viewModel.viewCrashLog(entry) },
                onShareLog = { entry -> viewModel.shareCrashLog(entry) },
                onShareLatest = viewModel::shareLatestCrashLog,
                onClearLogs = viewModel::clearCrashLogs
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Faz 3 Modül 4: Cihaz geneli rapor bölümü
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun DeviceReportSection(
    isGenerating: Boolean,
    onGenerate: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(DarkSurfaceVariant, RoundedCornerShape(12.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            "Tüm uygulamaları kapsayan gizlilik özeti oluşturur. Risk dağılımı, " +
            "tracker istatistikleri ve öneriler içerir. Metin veya PDF olarak paylaşılabilir.",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary
        )
        Button(
            onClick = onGenerate,
            enabled = !isGenerating,
            modifier = Modifier.fillMaxWidth(),
            colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = PrimaryGreen)
        ) {
            if (isGenerating) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp
                )
                Spacer(Modifier.width(8.dp))
                Text("Oluşturuluyor...", color = MaterialTheme.colorScheme.onPrimary)
            } else {
                Text("📊 Genel Rapor Oluştur", color = MaterialTheme.colorScheme.onPrimary)
            }
        }
    }
}

@Composable
private fun DeviceReportDialog(
    text: String,
    onShare: () -> Unit,
    onCopy: () -> Unit,
    onSavePdf: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Cihaz Gizlilik Raporu") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onCopy) { Text("Kopyala", color = TextSecondary) }
                TextButton(onClick = onSavePdf) { Text("PDF", color = PrimaryGreen) }
                TextButton(onClick = onShare) { Text("Paylaş", color = PrimaryGreen) }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Kapat", color = TextSecondary) }
        },
        containerColor = DarkSurfaceVariant
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// VPN modu bölümü
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun VpnModeSection(
    enabled: Boolean,
    onRequestEnable: () -> Unit,
    onDisable: () -> Unit
) {
    val context = LocalContext.current
    var showPrivateDnsDialog by remember { mutableStateOf(false) }

    // Gizli DNS çakışma uyarı dialogu
    if (showPrivateDnsDialog) {
        AlertDialog(
            onDismissRequest = { showPrivateDnsDialog = false },
            title = { Text("Gizli DNS Uyarısı") },
            text = {
                Text(
                    "VPN modu aktifken telefon ayarlarındaki 'Gizli DNS' özelliğini " +
                    "kapatmanız gerekebilir.\n\n" +
                    "Ayarlar → Bağlantı → Diğer bağlantı ayarları → Gizli DNS → Kapalı",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(onClick = { showPrivateDnsDialog = false }) {
                    Text("Anladım", color = PrimaryGreen)
                }
            },
            containerColor = DarkSurfaceVariant
        )
    }

    // Android standart VPN izin dialogu launcher
    val vpnPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            onRequestEnable()
            showPrivateDnsDialog = true
        }
        // Reddedilirse toggle zaten kapali kaldığından ekstra işlem gerekmez
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(DarkSurfaceVariant, RoundedCornerShape(12.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("🔬", style = MaterialTheme.typography.titleMedium)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Gelişmiş Tracker Tespiti",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    if (enabled) "VPN DNS yakalama aktif — gerçek bağlantı doğrulaması"
                    else "Kapalı — NetworkStats tabanlı (tahmini)",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (enabled) PrimaryGreen else TextSecondary
                )
            }
            Switch(
                checked = enabled,
                onCheckedChange = { wantEnabled ->
                    if (wantEnabled) {
                        try {
                            val prepareIntent = VpnService.prepare(context)
                            if (prepareIntent != null) {
                                // İzin gerekiyor — sistem dialogunu göster
                                vpnPermissionLauncher.launch(prepareIntent)
                            } else {
                                // Zaten izin verilmiş — direkt başlat ve uyarıyı göster
                                onRequestEnable()
                                showPrivateDnsDialog = true
                            }
                        } catch (e: Exception) {
                            // VPN hazırlık hatası — sessizce geç
                        }
                    } else {
                        onDisable()
                    }
                },
                colors = SwitchDefaults.colors(checkedThumbColor = PrimaryGreen)
            )
        }
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
        )
        Text(
            "Bu mod, hangi uygulamaların hangi sunuculara bağlandığını tam olarak\n" +
            "tespit eder. Trafik dışarıya gönderilmez — sadece DNS sorguları\n" +
            "cihazınızda analiz edilir.\n" +
            "Diğer VPN uygulamalarıyla aynı anda çalışamaz.",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// DNS reklam/tracker engelleme bölümü
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun DnsBlockingSection(
    mode: BlockingMode,
    adsBlockedToday: Int,
    trackersBlockedToday: Int,
    onModeChange: (BlockingMode) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(DarkSurfaceVariant, RoundedCornerShape(12.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Ana toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("🛡️", style = MaterialTheme.typography.titleMedium)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Reklam Engelleme",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    if (mode.isEnabled) "DNS seviyesinde reklam engelleme aktif"
                    else "Kapalı — yalnızca izleniyor, hiçbir şey engellenmiyor",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (mode.isEnabled) PrimaryGreen else TextSecondary
                )
            }
            Switch(
                checked = mode.isEnabled,
                onCheckedChange = { enabled ->
                    onModeChange(if (enabled) BlockingMode.BALANCED else BlockingMode.OFF)
                },
                colors = SwitchDefaults.colors(checkedThumbColor = PrimaryGreen)
            )
        }

        // Mod seçimi — yalnızca açıkken
        if (mode.isEnabled) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

            BlockingModeOption(
                emoji = "🟡",
                title = "Dengeli (önerilen)",
                description = "Sadece net reklam domainlerini engelle, uygulamaları etkileme",
                selected = mode == BlockingMode.BALANCED,
                color = AlertYellow,
                onSelect = { onModeChange(BlockingMode.BALANCED) }
            )
            BlockingModeOption(
                emoji = "🔴",
                title = "Agresif",
                description = "Reklam + tracker domainlerini engelle",
                selected = mode == BlockingMode.AGGRESSIVE,
                color = AlertRed,
                onSelect = { onModeChange(BlockingMode.AGGRESSIVE) }
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

            Text(
                "🚫 Bugün $adsBlockedToday reklam engellendi",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
            Text(
                "🚫 Bugün $trackersBlockedToday tracker engellendi",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
        }
    }
}

@Composable
private fun BlockingModeOption(
    emoji: String,
    title: String,
    description: String,
    selected: Boolean,
    color: androidx.compose.ui.graphics.Color,
    onSelect: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(emoji, style = MaterialTheme.typography.titleMedium)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                color = if (selected) color else MaterialTheme.colorScheme.onBackground,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
            )
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        androidx.compose.material3.RadioButton(
            selected = selected,
            onClick = onSelect,
            colors = androidx.compose.material3.RadioButtonDefaults.colors(selectedColor = color)
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Tracker izleme bölümü
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun TrackerSection(
    enabled: Boolean,
    lastScanLabel: String,
    scanState: ScanState,
    scanMessage: String,
    onToggle: (Boolean) -> Unit,
    onManualScan: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(DarkSurfaceVariant, RoundedCornerShape(12.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(Icons.Outlined.WifiFind, null, tint = if (enabled) PrimaryGreen else TextSecondary)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Tracker İzleme",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    if (enabled) "Her taramada tracker kontrolü yapılır"
                    else "Tracker kontrolü devre dışı",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
            Switch(
                checked = enabled,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(checkedThumbColor = PrimaryGreen)
            )
        }

        if (enabled) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Son tarama: $lastScanLabel",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )

                val isScanning = scanState == ScanState.SCANNING
                Button(
                    onClick = onManualScan,
                    enabled = !isScanning,
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = when (scanState) {
                            ScanState.COMPLETED -> PrimaryGreen.copy(alpha = 0.15f)
                            ScanState.ERROR -> AlertRed.copy(alpha = 0.15f)
                            else -> PrimaryGreen.copy(alpha = 0.15f)
                        },
                        contentColor = when (scanState) {
                            ScanState.COMPLETED -> PrimaryGreen
                            ScanState.ERROR -> AlertRed
                            else -> PrimaryGreen
                        },
                        disabledContainerColor = PrimaryGreen.copy(alpha = 0.08f),
                        disabledContentColor = PrimaryGreen.copy(alpha = 0.5f)
                    )
                ) {
                    if (isScanning) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            color = PrimaryGreen.copy(alpha = 0.6f),
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(
                        when (scanState) {
                            ScanState.IDLE -> "🔍 Manuel Tara"
                            ScanState.SCANNING -> "Taranıyor..."
                            ScanState.COMPLETED -> "✅ Tamamlandı"
                            ScanState.ERROR -> "❌ Hata"
                        },
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }

            // Tarama devam ederken progress bar ve durum mesajı
            if (scanState == ScanState.SCANNING) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = PrimaryGreen,
                    trackColor = PrimaryGreen.copy(alpha = 0.15f)
                )
                Text(
                    scanMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }

            // Tamamlanma veya hata mesajı
            if (scanState == ScanState.COMPLETED || scanState == ScanState.ERROR) {
                Text(
                    scanMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (scanState == ScanState.ERROR) AlertRed else PrimaryGreen
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Crash log bölümü
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun CrashLogSection(
    uiState: SettingsUiState,
    onViewLog: (CrashLogEntry) -> Unit,
    onShareLog: (CrashLogEntry) -> Unit,
    onShareLatest: () -> Unit,
    onClearLogs: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(DarkSurfaceVariant, RoundedCornerShape(12.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Başlık + sayaç
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Outlined.BugReport,
                    null,
                    tint = if (uiState.crashLogCount > 0) AlertRed else TextSecondary,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    "Hata Günlükleri",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            val countColor = when {
                uiState.crashLogCount == 0 -> TextSecondary
                uiState.crashLogCount < 3 -> AlertYellow
                else -> AlertRed
            }
            Text(
                text = if (uiState.crashLogCount == 0) "Temiz" else "${uiState.crashLogCount} kayıt",
                style = MaterialTheme.typography.labelMedium,
                color = countColor,
                fontWeight = FontWeight.SemiBold
            )
        }

        if (uiState.crashLogCount == 0) {
            Text(
                text = "Uygulama çökmesi yaşanmadı.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
        } else {
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

            // Crash log dosya listesi (en yeni üstte)
            uiState.crashLogFiles.forEach { entry ->
                CrashLogRow(
                    entry = entry,
                    onView = { onViewLog(entry) },
                    onShare = { onShareLog(entry) }
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

            // Alt eylem butonları
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onShareLatest,
                    modifier = Modifier.weight(1f),
                    colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                        contentColor = PrimaryGreen
                    )
                ) {
                    Icon(Icons.Outlined.Share, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Son Günlüğü Paylaş")
                }
                OutlinedButton(
                    onClick = onClearLogs,
                    modifier = Modifier.weight(1f),
                    colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                        contentColor = AlertRed
                    )
                ) {
                    Icon(Icons.Outlined.Delete, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Temizle")
                }
            }
        }
    }
}

@Composable
private fun CrashLogRow(
    entry: CrashLogEntry,
    onView: () -> Unit,
    onShare: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                Icons.Outlined.ErrorOutline,
                null,
                tint = AlertRed,
                modifier = Modifier.size(16.dp)
            )
            Column {
                Text(
                    entry.displayName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    "${entry.sizeKb} KB",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
            }
        }

        Row {
            IconButton(onClick = onView, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Outlined.Visibility,
                    contentDescription = "Görüntüle",
                    tint = TextSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }
            IconButton(onClick = onShare, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Outlined.Share,
                    contentDescription = "Paylaş",
                    tint = TextSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

/**
 * İki katmanlı crash log dialogu:
 *   Katman 1 — Kullanıcıya gösterilen özet (emoji + sade dil)
 *   Katman 2 — "Teknik Detay" butonu ile açılan ham stack trace
 */
@Composable
private fun CrashLogDialog(
    entry: CrashLogEntry,
    showTechnicalDetail: Boolean,
    onToggleTechnical: () -> Unit,
    onDismiss: () -> Unit
) {
    val summary = entry.summary
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Hata Raporu") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Katman 1: Kullanıcı dostu özet
                Text("📅 ${summary.timestamp}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface)
                Text("⚠️ ${summary.userMessage}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AlertRed)
                Text("🔧 Teknik kod: ${summary.technicalCode}",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary)
                Text("💡 ${summary.suggestion}",
                    style = MaterialTheme.typography.bodySmall,
                    color = PrimaryGreen)

                Spacer(Modifier.height(4.dp))

                // Katman 2 toggle butonu
                TextButton(
                    onClick = onToggleTechnical,
                    colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                        contentColor = TextSecondary
                    )
                ) {
                    Text(
                        if (showTechnicalDetail) "▲ Teknik Detayı Gizle"
                        else "▼ Teknik Detay (Geliştiriciler İçin)",
                        style = MaterialTheme.typography.labelMedium
                    )
                }

                // Ham stack trace — yalnızca toggle açıksa görünür
                if (showTechnicalDetail) {
                    Text(
                        text = entry.rawContent,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace
                        ),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Kapat", color = PrimaryGreen) }
        },
        containerColor = DarkSurfaceVariant
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Bildirim hassasiyeti bölümü
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun NotificationSensitivityCard(
    currentSensitivity: NotificationSensitivity,
    onSensitivityChange: (NotificationSensitivity) -> Unit
) {
    val options = listOf(
        Triple(NotificationSensitivity.CRITICAL_ONLY, "🔴", AlertRed),
        Triple(NotificationSensitivity.MEDIUM, "🟡", AlertYellow),
        Triple(NotificationSensitivity.ALL, "🟢", PrimaryGreen),
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (sensitivity, emoji, color) ->
            val isSelected = sensitivity == currentSensitivity
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        if (isSelected) DarkSurfaceVariant.copy(alpha = 0.8f) else DarkSurfaceVariant,
                        RoundedCornerShape(12.dp)
                    )
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(emoji, style = MaterialTheme.typography.titleMedium)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = sensitivity.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (isSelected) color else MaterialTheme.colorScheme.onBackground,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                    )
                    Text(
                        text = sensitivity.descriptionText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = isSelected,
                    onCheckedChange = { if (it) onSensitivityChange(sensitivity) },
                    colors = SwitchDefaults.colors(checkedThumbColor = color)
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Ortak bileşenler
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, top = 8.dp)
    )
}

@Composable
private fun MonitoringModeCard(
    currentMode: MonitoringMode,
    onModeChange: (MonitoringMode) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        MonitoringMode.entries.forEach { mode ->
            val isSelected = mode == currentMode
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        if (isSelected) DarkSurfaceVariant.copy(alpha = 0.8f) else DarkSurfaceVariant,
                        RoundedCornerShape(12.dp)
                    )
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = if (mode == MonitoringMode.PERIODIC)
                        Icons.Outlined.BatteryFull else Icons.Outlined.Speed,
                    contentDescription = null,
                    tint = if (isSelected) PrimaryGreen else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = mode.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (isSelected) PrimaryGreen else MaterialTheme.colorScheme.onBackground,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                    )
                    Text(
                        text = mode.descriptionText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = isSelected,
                    onCheckedChange = { if (it) onModeChange(mode) },
                    colors = SwitchDefaults.colors(checkedThumbColor = PrimaryGreen)
                )
            }
        }
    }
}

@Composable
private fun ToggleRow(
    icon: @Composable () -> Unit,
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(DarkSurfaceVariant, RoundedCornerShape(12.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        icon()
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedThumbColor = PrimaryGreen)
        )
    }
}
