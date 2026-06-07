package io.privacydroid.ui.notifications

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.privacydroid.data.local.entity.NotificationLogEntity
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
fun NotificationCenterScreen(
    onBack: () -> Unit,
    onAppClick: (String) -> Unit,
    viewModel: NotificationCenterViewModel = hiltViewModel()
) {
    val groups by viewModel.groupedNotifications.collectAsStateWithLifecycle()
    val unreadCount by viewModel.unreadCount.collectAsStateWithLifecycle()
    val currentFilter by viewModel.filter.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Bildirim Merkezi",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        if (unreadCount > 0) {
                            Text(
                                text = "$unreadCount okunmamış",
                                style = MaterialTheme.typography.labelSmall,
                                color = AlertRed
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri")
                    }
                },
                actions = {
                    if (unreadCount > 0) {
                        IconButton(onClick = viewModel::markAllAsRead) {
                            Icon(
                                Icons.Outlined.DoneAll,
                                contentDescription = "Tümünü okundu işaretle",
                                tint = PrimaryGreen
                            )
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Filtre çipleri
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterButton("Hepsi", currentFilter == NotificationFilter.ALL) {
                    viewModel.setFilter(NotificationFilter.ALL)
                }
                FilterButton("🔴 Kritik", currentFilter == NotificationFilter.CRITICAL) {
                    viewModel.setFilter(NotificationFilter.CRITICAL)
                }
                FilterButton("🟡 Orta", currentFilter == NotificationFilter.MEDIUM) {
                    viewModel.setFilter(NotificationFilter.MEDIUM)
                }
                FilterButton("✅ Okundu", currentFilter == NotificationFilter.READ) {
                    viewModel.setFilter(NotificationFilter.READ)
                }
            }

            if (groups.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Bildirim yok",
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextSecondary
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    groups.forEach { group ->
                        item(key = "header_${group.dateLabel}") {
                            Text(
                                text = "📅 ${group.dateLabel}",
                                style = MaterialTheme.typography.labelMedium,
                                color = TextSecondary,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }
                        items(group.items, key = { it.id }) { log ->
                            NotificationLogItem(
                                log = log,
                                onClick = {
                                    viewModel.markAsRead(log.id)
                                    if (log.packageName.isNotBlank()) onAppClick(log.packageName)
                                }
                            )
                        }
                        item(key = "divider_${group.dateLabel}") {
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                color = DarkSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterButton(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, fontSize = 11.sp) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = PrimaryGreen.copy(alpha = 0.2f),
            selectedLabelColor = PrimaryGreen
        )
    )
}

@Composable
private fun NotificationLogItem(
    log: NotificationLogEntity,
    onClick: () -> Unit
) {
    val riskColor = when (log.riskLevel) {
        "CRITICAL" -> AlertRed
        "HIGH" -> AlertOrange
        else -> TextSecondary
    }

    val bgColor = if (log.isRead) Color.Transparent else DarkSurfaceVariant.copy(alpha = 0.4f)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bgColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        // Risk seviyesi göstergesi
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(riskColor)
                .padding(top = 6.dp)
        )

        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = log.appName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (log.isRead) FontWeight.Normal else FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = formatNotifTime(log.detectedAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
            }

            Spacer(Modifier.height(2.dp))

            Text(
                text = log.message,
                style = MaterialTheme.typography.bodySmall,
                color = if (log.isRead) TextSecondary else MaterialTheme.colorScheme.onBackground,
                maxLines = 2
            )

            Spacer(Modifier.height(4.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .background(riskColor.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = when (log.riskLevel) {
                            "CRITICAL" -> "🔴 Kritik"
                            "HIGH" -> "🟡 Yüksek"
                            else -> "⚪ Düşük"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = riskColor
                    )
                }

                if (!log.isRead) {
                    Spacer(Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(PrimaryGreen)
                    )
                }
            }
        }
    }
}

private fun formatNotifTime(timeMs: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timeMs
    return when {
        diff < 60_000L -> "Az önce"
        diff < 3_600_000L -> "${diff / 60_000}dk önce"
        diff < 86_400_000L -> "${diff / 3_600_000}sa önce"
        else -> SimpleDateFormat("dd MMM HH:mm", Locale.getDefault()).format(Date(timeMs))
    }
}
