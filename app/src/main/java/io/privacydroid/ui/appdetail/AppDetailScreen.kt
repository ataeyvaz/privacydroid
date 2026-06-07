package io.privacydroid.ui.appdetail

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.NightsStay
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import io.privacydroid.data.local.dao.TrackerDomainSummary
import io.privacydroid.data.local.entity.AppPermissionSnapshotEntity
import io.privacydroid.data.local.entity.CorrelationResultEntity
import io.privacydroid.domain.model.AppDetail
import io.privacydroid.domain.model.TrackerCategory
import io.privacydroid.domain.model.classifyTrackerDomain
import io.privacydroid.domain.model.resolvePermissionDisplay
import io.privacydroid.ui.components.AppIconImage
import io.privacydroid.ui.components.RiskScoreRing
import io.privacydroid.util.ReportExporter
import io.privacydroid.ui.components.riskColor
import io.privacydroid.ui.theme.AlertRed
import io.privacydroid.ui.theme.DarkSurfaceVariant
import io.privacydroid.ui.theme.PrimaryGreen
import io.privacydroid.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppDetailScreen(
    packageName: String,
    onBack: () -> Unit,
    viewModel: AppDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val latestCorrelation by viewModel.latestCorrelation.collectAsStateWithLifecycle()
    val permissionHistory by viewModel.permissionHistory.collectAsStateWithLifecycle()
    val trackerDomains by viewModel.trackerDomains.collectAsStateWithLifecycle()
    val isBlockingException by viewModel.isBlockingException.collectAsStateWithLifecycle()
    val pagedLogs = viewModel.pagedLogs.collectAsLazyPagingItems()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = uiState.appDetail?.appName ?: "Uygulama Detayı",
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Geri")
                    }
                },
                actions = {
                    uiState.appDetail?.let { detail ->
                        IconButton(onClick = {
                            ReportExporter.shareText(
                                context = context,
                                text = viewModel.buildReportText(detail),
                                subject = "${detail.appName} Gizlilik Raporu"
                            )
                        }) {
                            Icon(Icons.Outlined.Share, contentDescription = "Paylaş")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        when {
            uiState.isLoading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = PrimaryGreen)
                }
            }

            uiState.error != null -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(uiState.error!!, color = MaterialTheme.colorScheme.error)
                }
            }

            uiState.appDetail != null -> {
                AppDetailContent(
                    detail = uiState.appDetail!!,
                    pagedLogs = pagedLogs,
                    packageName = packageName,
                    latestCorrelation = latestCorrelation,
                    permissionHistory = permissionHistory,
                    trackerDomains = trackerDomains,
                    blockingEnabled = !isBlockingException,
                    onSetBlockingEnabled = viewModel::setAppBlockingEnabled,
                    reportTextProvider = { viewModel.buildReportText(uiState.appDetail!!) },
                    kvkkTextProvider = { viewModel.buildKvkkText() },
                    modifier = Modifier.padding(paddingValues)
                )
            }
        }
    }
}

@Composable
private fun AppDetailContent(
    detail: AppDetail,
    pagedLogs: LazyPagingItems<LogRowItem>,
    packageName: String,
    latestCorrelation: CorrelationResultEntity?,
    permissionHistory: List<AppPermissionSnapshotEntity>,
    trackerDomains: List<TrackerDomainSummary>,
    blockingEnabled: Boolean,
    onSetBlockingEnabled: (Boolean) -> Unit,
    reportTextProvider: () -> String,
    kvkkTextProvider: () -> String?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { Spacer(modifier = Modifier.height(4.dp)) }

        // ---- Üst özet bölümü ----
        item {
            AppSummaryHeader(detail = detail, packageName = packageName)
        }

        // ---- Risk skoru bileşenleri ----
        item {
            RiskBreakdownCard(detail = detail)
        }

        // ---- Korelasyon kartı (kamera/mikrofon erişimi varsa) ----
        latestCorrelation?.let { corr ->
            item {
                SectionCard(title = "Son Erişim Korelasyonu") {
                    CorrelationCard(correlation = corr)
                }
            }
        }

        // ---- Haftalık frekans grafiği ----
        item {
            SectionCard(title = "Son 7 Gün") {
                AccessFrequencyChart(
                    data = detail.weeklyData,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // ---- Uygulama bazlı reklam engelleme toggle ----
        item {
            AppBlockingToggleCard(
                appName = detail.appName,
                enabled = blockingEnabled,
                onSetEnabled = onSetBlockingEnabled
            )
        }

        // ---- Aksiyon butonları ----
        item {
            ActionButtonsRow(
                packageName = packageName,
                appName = detail.appName,
                reportTextProvider = reportTextProvider,
                kvkkTextProvider = kvkkTextProvider
            )
        }

        // ---- Tracker bağlantı kartı ----
        if (trackerDomains.isNotEmpty()) {
            item {
                SectionCard(title = "Tracker Bağlantıları (Son 7 Gün)") {
                    TrackerDomainsCard(domains = trackerDomains)
                }
            }
        }

        // ---- İzin geçmişi zaman çizelgesi ----
        if (permissionHistory.size >= 2) {
            item {
                SectionCard(title = "İzin Geçmişi") {
                    PermissionTimeline(history = permissionHistory)
                }
            }
        }

        // ---- İzin erişim log başlığı ----
        item {
            Text(
                text = "İzin Geçmişi",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        // ---- Paged log listesi ----
        items(
            count = pagedLogs.itemCount,
            // id: Room primary key (Long), fallback: index.toLong() — tip tutarlılığı
            key = { index -> pagedLogs[index]?.id ?: index.toLong() }
        ) { index ->
            pagedLogs[index]?.let { item ->
                LogRow(item = item)
            }
        }

        item { Spacer(modifier = Modifier.height(24.dp)) }
    }
}

@Composable
private fun AppBlockingToggleCard(
    appName: String,
    enabled: Boolean,
    onSetEnabled: (Boolean) -> Unit
) {
    var showDisableDialog by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }

    if (showDisableDialog) {
        androidx.compose.material3.AlertDialog(
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
                androidx.compose.material3.TextButton(onClick = {
                    onSetEnabled(false)
                    showDisableDialog = false
                }) { Text("Devre Dışı Bırak", color = AlertRed) }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showDisableDialog = false }) {
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
            .padding(16.dp),
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
                if (enabled) "Bu uygulama için engelleme aktif"
                else "🔓 Bu uygulama için engelleme devre dışı",
                style = MaterialTheme.typography.bodySmall,
                color = if (enabled) PrimaryGreen else TextSecondary
            )
        }
        androidx.compose.material3.Switch(
            checked = enabled,
            onCheckedChange = { wantEnabled ->
                if (wantEnabled) onSetEnabled(true) else showDisableDialog = true
            },
            colors = androidx.compose.material3.SwitchDefaults.colors(checkedThumbColor = PrimaryGreen)
        )
    }
}

@Composable
private fun AppSummaryHeader(detail: AppDetail, packageName: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(DarkSurfaceVariant, RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        AppIconImage(
            packageName = packageName,
            appName = detail.appName,
            modifier = Modifier.size(56.dp)
        )

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = detail.appName,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = packageName,
                style = MaterialTheme.typography.labelMedium,
                color = TextSecondary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatChip("${detail.totalWeeklyCount} erişim", MaterialTheme.colorScheme.onSurfaceVariant)
                StatChip("${detail.backgroundCount} arka plan", AlertRed)
            }
        }

        RiskScoreRing(score = detail.riskScore, size = 80.dp, strokeWidth = 7.dp)
    }
}

@Composable
private fun RiskBreakdownCard(detail: AppDetail) {
    SectionCard(title = "Risk Bileşenleri") {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            RiskBar("Arka Plan Erişimi", detail.riskComponents.backgroundScore, 40f, AlertRed)
            RiskBar("Gece Erişimi", detail.riskComponents.nightScore, 30f, AlertRed.copy(alpha = 0.7f))
            RiskBar("İzin Çeşitliliği", detail.riskComponents.diversityScore, 20f, io.privacydroid.ui.theme.AlertOrange)
            RiskBar("Erişim Sıklığı", detail.riskComponents.frequencyScore, 10f, io.privacydroid.ui.theme.AlertYellow)
        }
    }
}

@Composable
private fun RiskBar(label: String, score: Float, maxScore: Float, color: Color) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            Text(
                "${score.toInt()}/${maxScore.toInt()}",
                style = MaterialTheme.typography.bodySmall,
                color = color
            )
        }
        Spacer(modifier = Modifier.height(2.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .background(DarkSurfaceVariant, RoundedCornerShape(2.dp))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction = (score / maxScore).coerceIn(0f, 1f))
                    .height(4.dp)
                    .background(color, RoundedCornerShape(2.dp))
            )
        }
    }
}

@Composable
private fun ActionButtonsRow(
    packageName: String,
    appName: String,
    reportTextProvider: () -> String,
    kvkkTextProvider: () -> String?
) {
    val context = LocalContext.current
    val now = java.util.Date()
    val dateStamp = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US).format(now)
    val prettyDate = java.text.SimpleDateFormat("d MMMM yyyy", java.util.Locale("tr")).format(now)

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = {
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", packageName, null)
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                    )
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = PrimaryGreen)
            ) {
                Icon(Icons.AutoMirrored.Outlined.OpenInNew, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Sistem Ayarları")
            }

            OutlinedButton(
                onClick = {
                    context.startActivity(
                        Intent(Intent.ACTION_DELETE).apply {
                            data = Uri.fromParts("package", packageName, null)
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                    )
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = AlertRed)
            ) {
                Icon(Icons.Outlined.Delete, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Kaldır")
            }
        }

        // ── Rapor: Paylaş + Kopyala ──
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilledTonalButton(
                onClick = {
                    ReportExporter.shareText(context, reportTextProvider(), "$appName Gizlilik Raporu")
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = DarkSurfaceVariant, contentColor = PrimaryGreen
                )
            ) {
                Icon(Icons.Outlined.Share, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Raporu Paylaş")
            }
            FilledTonalButton(
                onClick = {
                    ReportExporter.copyToClipboard(context, "Gizlilik Raporu", reportTextProvider())
                    Toast.makeText(context, "Rapor panoya kopyalandı", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = DarkSurfaceVariant, contentColor = TextSecondary
                )
            ) {
                Text("Kopyala")
            }
        }

        // ── PDF olarak kaydet ──
        FilledTonalButton(
            onClick = {
                val fileName = "rapor_${packageName}_$dateStamp.pdf"
                val uri = ReportExporter.savePdf(
                    context = context,
                    fileName = fileName,
                    title = "$appName Gizlilik Raporu",
                    subtitle = "PrivacyDroid · $prettyDate",
                    body = reportTextProvider()
                )
                if (uri != null) {
                    Toast.makeText(context, "PDF kaydedildi: $fileName", Toast.LENGTH_LONG).show()
                    ReportExporter.openPdf(context, uri)
                } else {
                    Toast.makeText(context, "PDF kaydedilemedi", Toast.LENGTH_SHORT).show()
                }
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = DarkSurfaceVariant, contentColor = PrimaryGreen
            )
        ) {
            Text("📄 PDF Olarak Kaydet")
        }

        // ── KVKK başvuru şablonu ──
        FilledTonalButton(
            onClick = {
                val kvkk = kvkkTextProvider()
                if (kvkk != null) {
                    ReportExporter.shareText(context, kvkk, "KVKK Başvurusu — $appName")
                } else {
                    Toast.makeText(context, "Rapor verisi henüz hazırlanıyor", Toast.LENGTH_SHORT).show()
                }
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = DarkSurfaceVariant, contentColor = TextSecondary
            )
        ) {
            Text("📋 KVKK Başvurusu Oluştur")
        }
    }
}

@Composable
private fun LogRow(item: LogRowItem) {
    val bgColor = if (item.isBackground) AlertRed.copy(alpha = 0.06f) else Color.Transparent

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (item.isBackground) bgColor else DarkSurfaceVariant,
                RoundedCornerShape(10.dp)
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // İzin renk noktası
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(item.permissionColor)
        )

        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = item.permissionLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    color = item.permissionColor,
                    fontWeight = FontWeight.Medium
                )
                if (item.isNight) {
                    Icon(
                        Icons.Outlined.NightsStay,
                        contentDescription = "Gece erişimi",
                        tint = AlertRed,
                        modifier = Modifier.size(14.dp)
                    )
                }
                if (item.isBackground) {
                    Text(
                        text = "• Arka Plan",
                        style = MaterialTheme.typography.labelSmall,
                        color = AlertRed
                    )
                }
            }
            if (item.durationLabel.isNotEmpty()) {
                Text(
                    text = "Süre: ${item.durationLabel}",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
        }

        Text(
            text = item.timeLabel,
            style = MaterialTheme.typography.labelMedium,
            color = TextSecondary
        )
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(DarkSurfaceVariant, RoundedCornerShape(12.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.SemiBold
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
        content()
    }
}

@Composable
private fun TrackerDomainsCard(domains: List<TrackerDomainSummary>) {
    val dateFmt = java.text.SimpleDateFormat("d MMM HH:mm", java.util.Locale("tr"))
        .also { it.timeZone = java.util.TimeZone.getDefault() }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        domains.forEach { domain ->
            val cat = classifyTrackerDomain(domain.domain)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("📡", style = MaterialTheme.typography.bodyMedium)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        domain.domain,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        "Kategori: ${cat.displayName}",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                    Text(
                        "Son 7 günde: ${domain.connectionCount} bağlantı  |  " +
                                "Son: ${dateFmt.format(java.util.Date(domain.lastSeen))}",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                    if (domain.totalBytes > 0) {
                        Text(
                            "↑ ${"%.1f".format(domain.totalBytes / 1_048_576.0)} MB",
                            style = MaterialTheme.typography.labelSmall,
                            color = AlertRed
                        )
                    }
                }
            }
            if (domains.last() != domain) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
            }
        }
    }
}

@Composable
private fun PermissionTimeline(history: List<AppPermissionSnapshotEntity>) {
    val dateFmt = java.text.SimpleDateFormat("d MMMM yyyy", java.util.Locale("tr"))
        .also { it.timeZone = java.util.TimeZone.getDefault() }

    history.forEachIndexed { index, snap ->
        val prevPerms = if (index > 0) history[index - 1].permissionSet() else emptySet()
        val currPerms = snap.permissionSet()

        val added   = currPerms - prevPerms
        val removed = prevPerms - currPerms
        val isFirst = index == 0

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Zaman çizelgesi noktası
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(if (added.isNotEmpty()) AlertRed else if (removed.isNotEmpty()) PrimaryGreen else TextSecondary)
                )
                if (index < history.lastIndex) {
                    Box(Modifier.width(2.dp).height(24.dp).background(DarkSurfaceVariant))
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "📅 ${dateFmt.format(java.util.Date(snap.snapshotDate))} — v${snap.versionName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Medium
                )
                if (isFirst) {
                    Text(
                        "   İlk yükleme: ${currPerms.size} izin",
                        style = MaterialTheme.typography.labelSmall, color = TextSecondary
                    )
                }
                added.forEach { perm ->
                    val d = resolvePermissionDisplay(perm)
                    Text(
                        "   ⚠️ YENİ: ${d.emoji} ${d.label}",
                        style = MaterialTheme.typography.labelSmall, color = AlertRed
                    )
                }
                removed.forEach { perm ->
                    val d = resolvePermissionDisplay(perm)
                    Text(
                        "   ✅ Kaldırıldı: ${d.emoji} ${d.label}",
                        style = MaterialTheme.typography.labelSmall, color = PrimaryGreen
                    )
                }
            }
        }
    }
}

@Composable
private fun StatChip(text: String, color: Color) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = color
    )
}
