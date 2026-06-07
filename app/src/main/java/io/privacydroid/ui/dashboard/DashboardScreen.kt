package io.privacydroid.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Timeline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.privacydroid.domain.model.DashboardStats
import io.privacydroid.domain.model.TrackerCategory
import io.privacydroid.ui.components.PermissionBanner
import io.privacydroid.ui.theme.AlertOrange
import io.privacydroid.ui.theme.AlertRed
import io.privacydroid.ui.theme.ColorCamera
import io.privacydroid.ui.theme.ColorLocation
import io.privacydroid.ui.theme.ColorMicrophone
import io.privacydroid.ui.theme.DarkSurfaceVariant
import io.privacydroid.ui.theme.PrimaryGreen
import io.privacydroid.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onAppClick: (String) -> Unit,
    onTimelineClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onNotificationCenterClick: () -> Unit = {},
    onTrackerConnectionsClick: () -> Unit = {},
    onBlockingDetailsClick: () -> Unit = {},
    onDebugClick: (() -> Unit)? = null,
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val hasPermission by viewModel.hasPermission.collectAsStateWithLifecycle()
    val monitoringStatus by viewModel.monitoringStatus.collectAsStateWithLifecycle()
    val unreadCount by viewModel.unreadNotificationCount.collectAsStateWithLifecycle()

    // İzin ayarlardan geri alınmışsa banner göster — uygulama çökmez
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.checkPermissionOnResume()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "PrivacyDroid",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    IconButton(onClick = onTimelineClick) {
                        Icon(Icons.Outlined.Timeline, contentDescription = "Zaman Çizelgesi")
                    }
                    if (onDebugClick != null) {
                        IconButton(onClick = onDebugClick) {
                            Icon(Icons.Outlined.BugReport, contentDescription = "Debug")
                        }
                    }
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Outlined.Settings, contentDescription = "Ayarlar")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // İzin iptali durumunda kırmızı banner — uygulama çökmez
            PermissionBanner(visible = !hasPermission)

            PullToRefreshBox(
                isRefreshing = uiState.isRefreshing,
                onRefresh = viewModel::refresh,
                modifier = Modifier.weight(1f)
            ) {
                if (!uiState.hasData && !uiState.isLoading) {
                    EmptyDashboard(onScanClick = viewModel::refresh)
                } else {
                    DashboardContent(
                        uiState = uiState,
                        monitoringStatus = monitoringStatus,
                        onAppClick = onAppClick,
                        onTimelineClick = onTimelineClick,
                        onSettingsClick = onSettingsClick,
                        onNotificationCenterClick = onNotificationCenterClick,
                        onTrackerConnectionsClick = onTrackerConnectionsClick,
                        onBlockingDetailsClick = onBlockingDetailsClick,
                        unreadNotificationCount = unreadCount,
                        viewModel = viewModel
                    )
                }
            }
        }
    }
}

@Composable
private fun DashboardContent(
    uiState: DashboardUiState,
    monitoringStatus: MonitoringStatusUiState,
    onAppClick: (String) -> Unit,
    onTimelineClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onNotificationCenterClick: () -> Unit,
    onTrackerConnectionsClick: () -> Unit,
    onBlockingDetailsClick: () -> Unit,
    unreadNotificationCount: Int,
    viewModel: DashboardViewModel
) {
    val blockingStats by viewModel.blockingStatsUi.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { Spacer(modifier = Modifier.height(4.dp)) }

        // Modül 4: İzleme durumu kartı
        item {
            MonitoringStatusCard(
                status = monitoringStatus,
                onClick = onSettingsClick
            )
        }

        // Bildirim merkezi kartı
        item {
            NotificationCenterCard(
                unreadCount = unreadNotificationCount,
                onClick = onNotificationCenterClick
            )
        }

        // Özet istatistik kartları
        item { StatRow(stats = uiState.stats, onTimelineClick = onTimelineClick) }

        // En riskli uygulama
        uiState.stats.mostRiskyApp?.let { risky ->
            item {
                RiskyAppCard(
                    appName = risky.appName,
                    backgroundCount = risky.backgroundAccessCount,
                    onClick = { onAppClick(risky.packageName) }
                )
            }
        }

        // 🛡️ Engelleme istatistikleri kartı (VPN aktif olduğunda görünür)
        if (blockingStats.isActive) {
            item { BlockingStatsCard(stats = blockingStats, onClick = onBlockingDetailsClick) }
        }

        // Tracker bağlantı kartı
        item {
            TrackerCard(
                items = uiState.trackerItems,
                isVpnMode = monitoringStatus.isVpnModeEnabled,
                onClick = onTrackerConnectionsClick
            )
        }

        item {
            Text(
                text = "Son Aktiviteler",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        if (uiState.recentAccessItems.isEmpty()) {
            item {
                EmptyLogsHint(onScanClick = viewModel::refresh)
            }
        } else {
            items(
                items = uiState.recentAccessItems,
                key = { it.id }  // Room primary key — kesinlikle benzersiz
            ) { item ->
                AccessLogCard(item = item, onClick = { onAppClick(item.packageName) })
            }
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}

// ── Engelleme İstatistikleri Kartı ────────────────────────────────────────────

@Composable
private fun BlockingStatsCard(stats: BlockingStatsUi, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(DarkSurfaceVariant, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "🛡️ Engelleme İstatistikleri",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.SemiBold
            )
            Text(text = "Detay ›", style = MaterialTheme.typography.labelMedium, color = PrimaryGreen)
        }
        Text(
            text = "Bugün",
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            BlockingStatItem("🚫 Reklam", stats.adsBlocked.toString(), AlertRed)
            BlockingStatItem("🚫 Tracker", stats.trackersBlocked.toString(), AlertOrange)
            BlockingStatItem("✅ İzin verilen", formatCount(stats.allowed), PrimaryGreen)
        }
    }
}

@Composable
private fun BlockingStatItem(label: String, value: String, color: androidx.compose.ui.graphics.Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            color = color,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary
        )
    }
}

private fun formatCount(n: Int): String =
    if (n >= 1000) "%,d".format(n) else n.toString()

// ── Bildirim Merkezi Kartı ────────────────────────────────────────────────────

@Composable
private fun NotificationCenterCard(unreadCount: Int, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(DarkSurfaceVariant, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(text = "🔔", style = MaterialTheme.typography.titleMedium)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (unreadCount > 0) "Bildirim Merkezi ($unreadCount yeni)" else "Bildirim Merkezi",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = if (unreadCount > 0) "Tıkla, okunmamış tespitleri görüntüle" else "Tüm gizlilik tespitleri",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary
            )
        }
        if (unreadCount > 0) {
            Box(
                modifier = Modifier
                    .background(AlertRed, RoundedCornerShape(10.dp))
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text(
                    text = unreadCount.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = androidx.compose.ui.graphics.Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        Text(text = "›", style = MaterialTheme.typography.titleLarge, color = TextSecondary)
    }
}

// ── Modül 4: İzleme Durumu Kartı ─────────────────────────────────────────────

@Composable
private fun MonitoringStatusCard(status: MonitoringStatusUiState, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(DarkSurfaceVariant, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Aktif/pasif göstergesi
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(if (status.isServiceRunning) PrimaryGreen else AlertOrange)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (status.isServiceRunning) "🟢 Aktif izleniyor" else "🟡 Periyodik izleme",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "Son tarama: ${status.lastScanLabel}  |  Sonraki: ${status.nextScanLabel}",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary
            )
            Text(
                text = "Mod: ${status.modeLabel}  |  Tracker: ${if (status.isTrackerEnabled) "Açık" else "Kapalı"}",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary
            )
        }
        Text(
            text = "›",
            style = MaterialTheme.typography.titleLarge,
            color = TextSecondary
        )
    }
}

@Composable
private fun StatRow(stats: DashboardStats, onTimelineClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        StatCard(
            modifier = Modifier.weight(1f),
            label = "Mikrofon",
            count = stats.microphoneCount,
            color = ColorMicrophone,
            onClick = onTimelineClick
        )
        StatCard(
            modifier = Modifier.weight(1f),
            label = "Kamera",
            count = stats.cameraCount,
            color = ColorCamera,
            onClick = onTimelineClick
        )
        StatCard(
            modifier = Modifier.weight(1f),
            label = "Konum",
            count = stats.locationCount,
            color = ColorLocation,
            onClick = onTimelineClick
        )
    }
}

@Composable
private fun StatCard(
    modifier: Modifier = Modifier,
    label: String,
    count: Int,
    color: Color,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .background(DarkSurfaceVariant, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.headlineMedium,
            color = color,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun RiskyAppCard(
    appName: String,
    backgroundCount: Int,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(DarkSurfaceVariant, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text(
                text = "En riskli uygulama",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = appName,
                style = MaterialTheme.typography.titleMedium,
                color = AlertOrange,
                fontWeight = FontWeight.SemiBold
            )
        }
        Text(
            text = "$backgroundCount arka plan erişimi",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun AccessLogCard(item: AccessLogItem, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(DarkSurfaceVariant, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(item.permissionColor)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.appName,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Medium
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = item.permissionLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = item.permissionColor
                )
                if (item.isBackground) {
                    Text(
                        text = "• Arka Plan",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
        Text(
            text = item.timeLabel,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun TrackerCard(items: List<TrackerItem>, isVpnMode: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(DarkSurfaceVariant, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                "📡 Aktif Tracker Bağlantıları",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                if (isVpnMode) "🔬 VPN modu aktif — DNS doğrulaması"
                else "📊 Tahmini — DNS doğrulaması için VPN modunu etkinleştirin",
                style = MaterialTheme.typography.labelSmall,
                color = if (isVpnMode) PrimaryGreen else TextSecondary,
                modifier = Modifier.weight(1f, fill = false),
                textAlign = TextAlign.End,
                maxLines = 2
            )
        }
        if (items.isEmpty()) {
            Text(
                "✅ Son 24 saatte tracker bağlantısı tespit edilmedi",
                style = MaterialTheme.typography.bodySmall,
                color = PrimaryGreen
            )
        } else {
            items.take(5).forEach { item ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val riskColor = when (item.category) {
                        TrackerCategory.SOCIAL, TrackerCategory.ADVERTISING,
                        TrackerCategory.ATTRIBUTION -> AlertRed
                        TrackerCategory.ANALYTICS, TrackerCategory.PUSH -> AlertOrange
                        else -> TextSecondary
                    }
                    Text(item.category.emoji, style = MaterialTheme.typography.bodyMedium)
                    Column(modifier = Modifier.weight(1f)) {
                        // VPN modunda gerçek domain ✅, değilse tahmini ❓
                        val domainLabel = if (isVpnMode) "${item.domain} ✅"
                                          else "~${item.domain} ❓"
                        Text(
                            "${item.appName} → $domainLabel",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onBackground,
                            maxLines = 1
                        )
                        Text(
                            "${item.connectionCount} bağlantı  |  ${item.lastSeenLabel}",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                    }
                    if (item.totalBytes > 0) {
                        Text(
                            "↑ ${formatBytes(item.totalBytes)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = riskColor
                        )
                    }
                }
            }
            if (items.size > 5) {
                Text(
                    "+${items.size - 5} daha fazla bağlantı — tümünü gör ›",
                    style = MaterialTheme.typography.labelSmall,
                    color = PrimaryGreen,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onClick)
                        .padding(top = 4.dp)
                )
            } else {
                Text(
                    "Tümünü gör ›",
                    style = MaterialTheme.typography.labelSmall,
                    color = PrimaryGreen,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onClick)
                        .padding(top = 4.dp)
                )
            }
        }
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes < 1_024 -> "${bytes}B"
    bytes < 1_048_576 -> "${"%.1f".format(bytes / 1024.0)}KB"
    else -> "${"%.1f".format(bytes / 1_048_576.0)}MB"
}

@Composable
private fun EmptyDashboard(onScanClick: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Henüz tarama yapılmadı",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "İlk taramayı başlatmak için dokunun",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(
                onClick = onScanClick,
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen)
            ) {
                Text("Şimdi Tara", color = MaterialTheme.colorScheme.onPrimary)
            }
        }
    }
}

/**
 * İlk tarama henüz yapılmadığında gösterilir.
 * Kullanıcıya ne yapacağını açıkça söyler.
 */
@Composable
private fun EmptyLogsHint(onScanClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(DarkSurfaceVariant, RoundedCornerShape(12.dp))
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Henüz aktivite kaydı yok",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = "Tarama başlatıldığında hangi uygulamaların ne zaman kamera, " +
                    "mikrofon veya konuma eriştiği burada görünecek.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Button(
            onClick = onScanClick,
            colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen)
        ) {
            Text("Şimdi Tara", color = MaterialTheme.colorScheme.onPrimary)
        }
    }
}
