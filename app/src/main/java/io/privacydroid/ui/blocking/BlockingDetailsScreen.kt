package io.privacydroid.ui.blocking

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import io.privacydroid.data.model.BlockingMode
import io.privacydroid.ui.common.TimeRange
import io.privacydroid.ui.components.AppIconImage
import io.privacydroid.ui.theme.AlertOrange
import io.privacydroid.ui.theme.AlertRed
import io.privacydroid.ui.theme.DarkSurfaceVariant
import io.privacydroid.ui.theme.PrimaryGreen
import io.privacydroid.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlockingDetailsScreen(
    onBack: () -> Unit,
    viewModel: BlockingDetailsViewModel = hiltViewModel()
) {
    val summary by viewModel.summary.collectAsStateWithLifecycle()
    val timeRange by viewModel.timeRange.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val topBlocked by viewModel.topBlocked.collectAsStateWithLifecycle()
    val blockingMode by viewModel.blockingMode.collectAsStateWithLifecycle()
    val blocked = viewModel.blockedPaged.collectAsLazyPagingItems()
    val allowed = viewModel.allowedPaged.collectAsLazyPagingItems()

    var selectedTab by remember { mutableIntStateOf(0) }
    var selectedBlocked by remember { mutableStateOf<BlockedRowUi?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Engelleme İstatistikleri",
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
            // ── Üst özet ──
            SummaryHeader(summary = summary, timeRangeLabel = timeRange.label)

            // ── Zaman filtresi ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
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

            // ── Sekmeler ──
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.background,
                contentColor = PrimaryGreen
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("🚫 Engellenenler") }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("✅ İzin Verilenler") }
                )
            }

            // ── Arama ──
            OutlinedTextField(
                value = searchQuery,
                onValueChange = viewModel::setSearchQuery,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                placeholder = { Text("Domain veya uygulama ara") },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                singleLine = true
            )

            if (selectedTab == 0) {
                BlockedList(
                    blocked = blocked,
                    topBlocked = topBlocked,
                    onRowClick = { selectedBlocked = it }
                )
            } else {
                AllowedList(allowed = allowed)
            }
        }
    }

    selectedBlocked?.let { row ->
        BlockedDetailDialog(
            row = row,
            blockingMode = blockingMode,
            onAllow = {
                viewModel.allowApp(row.packageName, row.appName)
                selectedBlocked = null
            },
            onDismiss = { selectedBlocked = null }
        )
    }
}

@Composable
private fun SummaryHeader(summary: BlockingSummaryUi, timeRangeLabel: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .background(DarkSurfaceVariant, RoundedCornerShape(12.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            SummaryItem("🚫 Reklam", summary.adsBlocked.toString(), AlertRed)
            SummaryItem("🚫 Tracker", summary.trackersBlocked.toString(), AlertOrange)
            SummaryItem("✅ İzin verilen", formatCount(summary.allowed), PrimaryGreen)
        }
        Text(
            text = "📅 $timeRangeLabel",
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary
        )
    }
}

@Composable
private fun SummaryItem(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            color = color,
            fontWeight = FontWeight.Bold
        )
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
    }
}

@Composable
private fun BlockedList(
    blocked: LazyPagingItems<BlockedRowUi>,
    topBlocked: List<TopBlockedDomain>,
    onRowClick: (BlockedRowUi) -> Unit
) {
    if (blocked.itemCount == 0) {
        EmptyHint("Bu aralıkta engellenen domain yok")
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (topBlocked.isNotEmpty()) {
            item {
                TopBlockedCard(topBlocked)
            }
        }
        items(
            count = blocked.itemCount,
            key = { index -> blocked[index]?.id ?: index.toLong() }
        ) { index ->
            blocked[index]?.let { row -> BlockedRow(row = row, onClick = { onRowClick(row) }) }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun TopBlockedCard(topBlocked: List<TopBlockedDomain>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(DarkSurfaceVariant, RoundedCornerShape(12.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = "En çok engellenen domainler",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground
        )
        topBlocked.forEachIndexed { index, item ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${index + 1}. ${item.domain}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "${item.count} kez",
                    style = MaterialTheme.typography.bodySmall,
                    color = AlertRed
                )
            }
        }
    }
}

@Composable
private fun BlockedRow(row: BlockedRowUi, onClick: () -> Unit) {
    val typeColor = if (row.blockType == "AD") AlertRed else AlertOrange
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
            packageName = row.packageName,
            appName = row.appName,
            modifier = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp))
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = row.domain,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Medium,
                maxLines = 1
            )
            Text(
                text = row.appName,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                maxLines = 1
            )
            Text(
                text = if (row.blockType == "AD") "🔴 Reklam" else "🟠 Tracker",
                style = MaterialTheme.typography.labelSmall,
                color = typeColor
            )
        }
        Text(
            text = row.timeLabel,
            style = MaterialTheme.typography.labelMedium,
            color = TextSecondary
        )
    }
}

@Composable
private fun AllowedList(allowed: LazyPagingItems<AllowedRowUi>) {
    if (allowed.itemCount == 0) {
        EmptyHint("Bu aralıkta izin verilen kayıt yok")
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(
            count = allowed.itemCount,
            key = { index -> allowed[index]?.id ?: index.toLong() }
        ) { index ->
            allowed[index]?.let { row -> AllowedRow(row = row) }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun AllowedRow(row: AllowedRowUi) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(DarkSurfaceVariant, RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        AppIconImage(
            packageName = row.packageName,
            appName = row.appName,
            modifier = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp))
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = row.domain,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Medium,
                maxLines = 1
            )
            Text(
                text = row.appName,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                maxLines = 1
            )
            Text(
                text = allowReasonLabel(row.allowReason),
                style = MaterialTheme.typography.labelSmall,
                color = PrimaryGreen
            )
        }
        Text(
            text = row.timeLabel,
            style = MaterialTheme.typography.labelMedium,
            color = TextSecondary
        )
    }
}

@Composable
private fun BlockedDetailDialog(
    row: BlockedRowUi,
    blockingMode: BlockingMode,
    onAllow: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(DarkSurfaceVariant, RoundedCornerShape(16.dp))
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "${row.domain} — ${row.timeLabel}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "Uygulama: ${row.appName}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "Tip: ${if (row.blockType == "AD") "Reklam sunucusu" else "Tracker sunucusu"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "Engelleme modu: ${blockingModeLabel(blockingMode)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Neden engellendi:",
                style = MaterialTheme.typography.labelMedium,
                color = AlertRed,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = blockReason(row.domain, row.blockType),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground
            )

            if (row.isRealPackage) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Bu uygulamaya izin vermek ister misiniz?",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Kapat", color = TextSecondary)
                    }
                    Button(
                        onClick = onAllow,
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen)
                    ) {
                        Text("İzin Ver", color = MaterialTheme.colorScheme.onPrimary)
                    }
                }
            } else {
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
}

@Composable
private fun EmptyHint(text: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = text, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
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

private fun allowReasonLabel(reason: String): String = when (reason) {
    "WHITELIST" -> "✅ Beyaz listede (CDN/Banka/Google)"
    "USER_EXCEPTION" -> "🔓 Kullanıcı istisnası"
    else -> "➡️ Normal trafik (listede yok)"
}

private fun blockReason(domain: String, blockType: String): String = if (blockType == "AD") {
    "'$domain' ad_domains.txt listesinde kayıtlı bir reklam sunucusudur. " +
        "Kullandığınız uygulamalar üzerinden reklam profili oluşturmak için kullanılır."
} else {
    "'$domain' tracker_domains.txt listesinde kayıtlı bir takip sunucusudur. " +
        "Uygulama içi davranışınızı izleyip profillemek için kullanılır."
}

private fun blockingModeLabel(mode: BlockingMode): String = when (mode) {
    BlockingMode.OFF -> "Kapalı"
    BlockingMode.BALANCED -> "Dengeli"
    BlockingMode.AGGRESSIVE -> "Agresif"
}

private fun formatCount(n: Int): String = if (n >= 1000) "%,d".format(n) else n.toString()
