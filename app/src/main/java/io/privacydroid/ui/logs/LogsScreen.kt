package io.privacydroid.ui.logs

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Camera
import androidx.compose.material.icons.outlined.Contacts
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Sms
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.privacydroid.data.local.entity.TrackerConnectionEntity
import io.privacydroid.data.model.PermissionLog
import io.privacydroid.data.model.PermissionType
import io.privacydroid.domain.model.LogFilter
import io.privacydroid.domain.model.TrackerCategory
import io.privacydroid.ui.components.FilterBar
import io.privacydroid.ui.theme.AlertRed
import io.privacydroid.ui.theme.ColorCamera
import io.privacydroid.ui.theme.ColorContacts
import io.privacydroid.ui.theme.ColorLocation
import io.privacydroid.ui.theme.ColorMicrophone
import io.privacydroid.ui.theme.ColorSms
import io.privacydroid.ui.theme.DarkSurfaceVariant
import io.privacydroid.ui.theme.PrimaryGreen
import io.privacydroid.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogsScreen(viewModel: LogsViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val selectedLog by viewModel.selectedLog.collectAsStateWithLifecycle()

    // Detay popup
    selectedLog?.let { log ->
        LogDetailDialog(
            log = log,
            message = viewModel.buildFriendlyMessage(log),
            onDismiss = viewModel::dismissDetail
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "İzin Erişim Günlüğü",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
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
            FilterBar(
                filter = uiState.filter,
                onFilterChange = viewModel::updateFilter
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))

            when {
                uiState.isLoading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Yükleniyor…", color = TextSecondary)
                    }
                }

                uiState.isEmpty -> {
                    EmptyLogsState(filter = uiState.filter)
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(1.dp)
                    ) {
                        item { Spacer(Modifier.height(4.dp)) }

                        if (uiState.filter.showTrackerOnly) {
                            itemsIndexed(
                                items = uiState.trackerLogs,
                                key = { _, t -> t.id }
                            ) { _, tracker ->
                                TrackerLogRow(tracker = tracker)
                            }
                        } else {
                            itemsIndexed(
                                items = uiState.logs,
                                key = { _, log -> log.id }
                            ) { _, log ->
                                LogRow(
                                    log = log,
                                    onClick = { viewModel.selectLog(log) }
                                )
                            }
                        }

                        item { Spacer(Modifier.height(16.dp)) }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Tracker log satırı (Modül 3)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun TrackerLogRow(tracker: TrackerConnectionEntity) {
    val category = TrackerCategory.fromString(tracker.category)
    val rowBg = DarkSurfaceVariant
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(rowBg)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(category.emoji, style = MaterialTheme.typography.bodyMedium)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = tracker.appName,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Medium,
                maxLines = 1
            )
            Text(
                text = tracker.domain,
                style = MaterialTheme.typography.bodySmall,
                color = io.privacydroid.ui.theme.AlertRed,
                maxLines = 1
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = formatShortDate(tracker.detectedAt),
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary
            )
            if (tracker.bytesSent > 0) {
                val kb = "%.1f".format(tracker.bytesSent / 1024.0)
                Text(
                    text = "↑ ${kb}KB",
                    style = MaterialTheme.typography.labelSmall,
                    color = io.privacydroid.ui.theme.AlertOrange
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Log satırı
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun LogRow(log: PermissionLog, onClick: () -> Unit) {
    val isNight = log.accessTime.isNightTime()
    val rowBg = when {
        log.isBackground && isNight -> AlertRed.copy(alpha = 0.07f)
        log.isBackground -> DarkSurfaceVariant
        else -> DarkSurfaceVariant.copy(alpha = 0.5f)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(rowBg)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // İzin ikonu
        Icon(
            imageVector = permissionIcon(log.permissionType),
            contentDescription = log.permissionType.displayName,
            tint = permissionColor(log.permissionType),
            modifier = Modifier.size(20.dp)
        )

        // İzin adı
        Text(
            text = log.permissionType.displayName,
            style = MaterialTheme.typography.bodyMedium,
            color = permissionColor(log.permissionType),
            modifier = Modifier.weight(0.9f),
            maxLines = 1
        )

        // Uygulama adı
        Text(
            text = log.appName,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1.2f),
            maxLines = 1
        )

        // Tarih/saat
        Text(
            text = formatShortDate(log.accessTime),
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary,
            modifier = Modifier.weight(0.8f)
        )

        // Arka plan/ön plan göstergesi
        if (log.isBackground) {
            Text(
                text = "🔴",
                style = MaterialTheme.typography.labelSmall
            )
        } else {
            Text(
                text = "✅",
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Detay popup
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun LogDetailDialog(
    log: PermissionLog,
    message: String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = permissionIcon(log.permissionType),
                contentDescription = null,
                tint = permissionColor(log.permissionType),
                modifier = Modifier.size(28.dp)
            )
        },
        title = {
            Text(
                "${log.permissionType.displayName} Erişimi",
                style = MaterialTheme.typography.titleMedium
            )
        },
        text = {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Tamam", color = PrimaryGreen)
            }
        },
        containerColor = DarkSurfaceVariant
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Boş durum
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun EmptyLogsState(filter: LogFilter) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            val isFiltered = filter != LogFilter()
            Text(
                text = if (isFiltered) "Bu kriterde erişim kaydı bulunamadı"
                else "Henüz kayıt yok",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = if (isFiltered) "Filtreleri değiştirerek daha geniş arama yapabilirsiniz"
                else "Uygulama arka planda izlemeye devam ediyor. Erişimler tespit edildiğinde burada görünecek.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Yardımcılar
// ─────────────────────────────────────────────────────────────────────────────

private val shortDateFmt = SimpleDateFormat("d MMM HH:mm", Locale("tr"))
    .apply { timeZone = java.util.TimeZone.getDefault() }

private fun formatShortDate(timeMs: Long): String =
    shortDateFmt.format(Date(timeMs))

private fun Long.isNightTime(): Boolean {
    val cal = java.util.Calendar.getInstance().apply { timeInMillis = this@isNightTime }
    return cal.get(java.util.Calendar.HOUR_OF_DAY) < 6
}

@Composable
private fun permissionColor(type: PermissionType): Color = when (type) {
    PermissionType.CAMERA -> ColorCamera
    PermissionType.MICROPHONE -> ColorMicrophone
    PermissionType.LOCATION_FINE, PermissionType.LOCATION_COARSE -> ColorLocation
    PermissionType.CONTACTS -> ColorContacts
    PermissionType.SMS -> ColorSms
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

private fun permissionIcon(type: PermissionType): ImageVector = when (type) {
    PermissionType.CAMERA -> Icons.Outlined.Camera
    PermissionType.MICROPHONE -> Icons.Outlined.Mic
    PermissionType.LOCATION_FINE, PermissionType.LOCATION_COARSE -> Icons.Outlined.LocationOn
    PermissionType.CONTACTS -> Icons.Outlined.Contacts
    PermissionType.SMS -> Icons.Outlined.Sms
    else -> Icons.Outlined.MoreHoriz
}
