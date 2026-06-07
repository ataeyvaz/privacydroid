package io.privacydroid.ui.trackers

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.privacydroid.domain.model.TrackerCategory
import io.privacydroid.domain.model.TrackerDomainInfo
import io.privacydroid.ui.common.TimeRange
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
fun TrackerConnectionsScreen(
    onBack: () -> Unit,
    viewModel: TrackerConnectionsViewModel = hiltViewModel()
) {
    val items by viewModel.items.collectAsStateWithLifecycle()
    val timeRange by viewModel.timeRange.collectAsStateWithLifecycle()
    val categoryFilter by viewModel.categoryFilter.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()

    var selected by remember { mutableStateOf<TrackerRowUi?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Tüm Tracker Bağlantıları",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Kategori filtreleri
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TrackerFilterCategory.entries.forEach { filter ->
                    FilterChipSmall(
                        label = filter.label,
                        selected = categoryFilter == filter,
                        onClick = { viewModel.setCategoryFilter(filter) }
                    )
                }
            }

            // Zaman filtreleri
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TimeRange.entries.forEach { range ->
                    FilterChipSmall(
                        label = range.label,
                        selected = timeRange == range,
                        onClick = { viewModel.setTimeRange(range) }
                    )
                }
            }

            // Uygulama bazlı arama
            OutlinedTextField(
                value = searchQuery,
                onValueChange = viewModel::setSearchQuery,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                placeholder = { Text("Uygulama veya domain ara") },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                singleLine = true
            )

            if (items.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Bu kriterlere uygun tracker bağlantısı yok",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(
                        items = items,
                        key = { "${it.packageName}|${it.domain}" }
                    ) { item ->
                        TrackerRow(item = item, onClick = { selected = item })
                    }
                    item { Spacer(Modifier.height(16.dp)) }
                }
            }
        }
    }

    selected?.let { item ->
        TrackerDetailDialog(item = item, onDismiss = { selected = null })
    }
}

@Composable
private fun FilterChipSmall(label: String, selected: Boolean, onClick: () -> Unit) {
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
private fun TrackerRow(item: TrackerRowUi, onClick: () -> Unit) {
    val riskColor = categoryColor(item.category)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(DarkSurfaceVariant, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        AppIconImage(
            packageName = item.packageName,
            appName = item.appName,
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
        )
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = item.appName,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1
                )
                Text(
                    text = if (item.dnsVerified) "✅" else "❓",
                    style = MaterialTheme.typography.labelSmall
                )
            }
            Text(
                text = item.domain,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                maxLines = 1
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "${item.category.emoji} ${categoryShortLabel(item.category)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = riskColor
                )
                Text(
                    text = "• ${item.connectionCount} bağlantı",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            if (item.totalBytes > 0) {
                Text(
                    text = formatMb(item.totalBytes),
                    style = MaterialTheme.typography.labelMedium,
                    color = riskColor,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Text(
                text = formatRelative(item.lastSeenMs),
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary
            )
        }
    }
}

@Composable
private fun TrackerDetailDialog(item: TrackerRowUi, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(DarkSurfaceVariant, RoundedCornerShape(16.dp))
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "${item.appName} → ${item.domain}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            DetailLine("Kategori", item.category.displayName)
            DetailLine("İlk görülme", formatFull(item.firstSeenMs))
            DetailLine("Son görülme", formatFull(item.lastSeenMs))
            DetailLine("Toplam bağlantı", item.connectionCount.toString())
            DetailLine(
                "DNS doğrulaması",
                if (item.dnsVerified) "✅ VPN ile doğrulandı" else "❓ Doğrulanmadı (tahmini)"
            )
            if (item.totalBytes > 0) {
                DetailLine("Veri miktarı", formatMb(item.totalBytes))
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Bu domain hakkında:",
                style = MaterialTheme.typography.labelMedium,
                color = PrimaryGreen,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "${item.domain} — ${TrackerDomainInfo.describe(item.domain, item.category)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismiss) {
                    Text("Kapat", color = PrimaryGreen)
                }
            }
        }
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "$label:",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
            modifier = Modifier.width(120.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f)
        )
    }
}

private fun categoryColor(category: TrackerCategory) = when (category) {
    TrackerCategory.SOCIAL, TrackerCategory.ADVERTISING, TrackerCategory.ATTRIBUTION -> AlertRed
    TrackerCategory.ANALYTICS, TrackerCategory.PUSH -> AlertOrange
    else -> TextSecondary
}

private fun categoryShortLabel(category: TrackerCategory) = when (category) {
    TrackerCategory.SOCIAL -> "SOCIAL"
    TrackerCategory.ANALYTICS -> "ANALYTICS"
    TrackerCategory.ADVERTISING -> "ADS"
    TrackerCategory.CDN -> "CDN"
    TrackerCategory.CRASH_REPORTING -> "CRASH"
    TrackerCategory.ATTRIBUTION -> "ATTRIBUTION"
    TrackerCategory.PUSH -> "PUSH"
    TrackerCategory.UNKNOWN -> "OTHER"
}

private fun formatMb(bytes: Long): String = when {
    bytes < 1_048_576 -> "${"%.2f".format(bytes / 1_048_576.0)} MB"
    else -> "${"%.1f".format(bytes / 1_048_576.0)} MB"
}

private fun formatRelative(timeMs: Long): String {
    val diff = System.currentTimeMillis() - timeMs
    return when {
        diff < 60_000L -> "Az önce"
        diff < 3_600_000L -> "${diff / 60_000} dk önce"
        diff < 86_400_000L -> "${diff / 3_600_000} sa önce"
        else -> "${diff / 86_400_000} gün önce"
    }
}

private fun formatFull(timeMs: Long): String =
    SimpleDateFormat("d MMM HH:mm", Locale("tr")).format(Date(timeMs))
