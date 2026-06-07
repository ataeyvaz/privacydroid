package io.privacydroid.ui.apps

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.privacydroid.domain.model.AppInfo
import io.privacydroid.domain.model.AppNetworkStats
import io.privacydroid.domain.model.AppRiskLevel
import io.privacydroid.domain.model.toMbString
import io.privacydroid.ui.components.AppIconImage
import io.privacydroid.ui.theme.AlertOrange
import io.privacydroid.ui.theme.AlertRed
import io.privacydroid.ui.theme.DarkSurfaceVariant
import io.privacydroid.ui.theme.PrimaryGreen
import io.privacydroid.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AppsScreen(viewModel: AppsViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val permissionUsageCounts by viewModel.permissionUsageCounts.collectAsStateWithLifecycle()
    val blockingExceptions by viewModel.blockingExceptionPackages.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Uygulama detay bottom sheet
    uiState.selectedApp?.let { app ->
        AppDetailSheet(
            app = app,
            permissionUsageCounts = permissionUsageCounts,
            blockingEnabled = app.packageName !in blockingExceptions,
            onSetBlockingEnabled = { enabled -> viewModel.setAppBlockingEnabled(app, enabled) },
            onDismiss = viewModel::dismissDetail
        )
    }

    // Modül 5: Kritik izin tarama bottom sheet
    if (uiState.showCriticalFilterSheet) {
        CriticalPermissionFilterSheet(
            uiState = uiState,
            onToggleFilter = viewModel::toggleCriticalPermissionFilter,
            onShare = { viewModel.shareCriticalReport(context) },
            onDismiss = viewModel::toggleCriticalFilterSheet
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("Uygulamalar", style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold)
                },
                actions = {
                    IconButton(onClick = viewModel::toggleCriticalFilterSheet) {
                        Icon(Icons.Outlined.FilterList, contentDescription = "Kritik İzin Filtresi")
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
                .padding(horizontal = 12.dp)
        ) {
            // Arama + sistem uygulamaları toggle
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = uiState.searchQuery,
                    onValueChange = viewModel::setSearchQuery,
                    placeholder = { Text("Uygulama ara…", color = TextSecondary) },
                    leadingIcon = { Icon(Icons.Outlined.Search, null, tint = TextSecondary) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PrimaryGreen,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                        focusedContainerColor = DarkSurfaceVariant,
                        unfocusedContainerColor = DarkSurfaceVariant
                    )
                )
            }

            // Özet header + sistem toggle
            if (!uiState.isLoading && uiState.filteredApps.isNotEmpty()) {
                val (high, medium, low) = viewModel.riskCounts()
                val total = uiState.filteredApps.size

                // Satır 1: toplam + risk sayıları
                Text(
                    text = "$total uygulama — $high Riskli, $medium Dikkat, $low Temiz",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextSecondary,
                    modifier = Modifier.padding(bottom = 4.dp)
                )

                // Satır 2: risk chip'leri + sistem toggle
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RiskChip("🔴 $high", AlertRed)
                    RiskChip("🟡 $medium", AlertOrange)
                    RiskChip("🟢 $low", PrimaryGreen)
                    Spacer(Modifier.weight(1f))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Sistem", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                        Spacer(Modifier.width(4.dp))
                        Switch(
                            checked = uiState.includeSystemApps,
                            onCheckedChange = { viewModel.toggleSystemApps() },
                            modifier = Modifier.height(24.dp),
                            colors = SwitchDefaults.colors(checkedThumbColor = PrimaryGreen)
                        )
                    }
                }
            }

            when {
                uiState.isLoading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = PrimaryGreen)
                            Spacer(Modifier.height(8.dp))
                            Text("Uygulamalar taranıyor…", color = TextSecondary,
                                style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                uiState.filteredApps.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            if (uiState.searchQuery.isBlank()) "Uygulama bulunamadı"
                            else "\"${uiState.searchQuery}\" için sonuç yok",
                            color = TextSecondary
                        )
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        itemsIndexed(
                            items = uiState.filteredApps,
                            key = { _, app -> app.packageName }
                        ) { _, app ->
                            AppRow(
                                app = app,
                                isBlockingException = app.packageName in blockingExceptions,
                                onClick = { viewModel.selectApp(app) }
                            )
                        }
                        item { Spacer(Modifier.height(16.dp)) }
                    }
                }
            }
        }
    }
}

// ── Modül 5: Kritik İzin Tarama Bottom Sheet ──────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun CriticalPermissionFilterSheet(
    uiState: AppsUiState,
    onToggleFilter: (CriticalPermissionFilter) -> Unit,
    onShare: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Başlık + özet
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "Kritik İzin Taraması",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "${uiState.criticalFilterApps.size} uygulama",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextSecondary
                )
            }

            // Filtre chip'leri — FlowRow ile otomatik satır
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                CriticalPermissionFilter.entries.forEach { filter ->
                    val isSelected = filter in uiState.selectedCriticalFilters
                    FilterChip(
                        selected = isSelected,
                        onClick = { onToggleFilter(filter) },
                        label = { Text("${filter.emoji} ${filter.label}",
                            style = MaterialTheme.typography.labelMedium) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AlertRed.copy(alpha = 0.2f),
                            selectedLabelColor = AlertRed,
                            labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

            // Sonuç listesi
            if (uiState.criticalFilterApps.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().height(120.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Seçilen izinlere sahip uygulama bulunamadı",
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            } else {
                uiState.criticalFilterApps.forEach { app ->
                    CriticalAppCard(
                        app = app,
                        selectedFilters = uiState.selectedCriticalFilters
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // Rapor paylaş butonu
            Button(
                onClick = onShare,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen)
            ) {
                Icon(Icons.Outlined.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Tümünü Raporla")
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun CriticalAppCard(app: AppInfo, selectedFilters: Set<CriticalPermissionFilter>) {
    val riskColor = when (app.riskLevel) {
        AppRiskLevel.HIGH   -> AlertRed
        AppRiskLevel.MEDIUM -> AlertOrange
        AppRiskLevel.LOW    -> PrimaryGreen
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(DarkSurfaceVariant, RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
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
                AppIconImage(app.packageName, app.appName, modifier = Modifier.size(36.dp))
                Column {
                    Text(app.appName, style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1)
                    Text(app.packageName, style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary, maxLines = 1)
                }
            }
            Text(
                "${app.riskLevel.emoji} Risk ${app.dangerousPermissions.size}",
                style = MaterialTheme.typography.labelSmall,
                color = riskColor,
                fontWeight = FontWeight.Bold
            )
        }
        // Seçili filtre izinleri
        val filtersToShow = if (selectedFilters.isEmpty()) {
            CriticalPermissionFilter.entries.filter { f ->
                f.permissionName in app.requestedPermissions
            }
        } else {
            selectedFilters.filter { f -> f.permissionName in app.requestedPermissions }
        }
        filtersToShow.forEach { f ->
            Text(
                "${f.emoji} ${f.label}",
                style = MaterialTheme.typography.bodySmall,
                color = riskColor.copy(alpha = 0.9f),
                modifier = Modifier.padding(start = 4.dp)
            )
        }
    }
}

@Composable
private fun AppRow(app: AppInfo, isBlockingException: Boolean = false, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(DarkSurfaceVariant, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        AppIconImage(app.packageName, app.appName, modifier = Modifier.size(40.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                // 🔓 = bu uygulama reklam engellemeden hariç tutulmuş
                text = if (isBlockingException) "🔓 ${app.appName}" else app.appName,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1)
            Text(app.packageName, style = MaterialTheme.typography.labelSmall,
                color = TextSecondary, maxLines = 1)
            // Ağ istatistikleri
            if (app.networkStats.txBytes24h > 0) {
                Text(
                    "↑ ${app.networkStats.txBytes24h.toMbString()}  " +
                            "↓ ${app.networkStats.rxBytes24h.toMbString()}",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (app.networkStats.isAnomaly) AlertRed else TextSecondary
                )
            }
        }

        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            // Risk rozeti
            Text(
                "${app.riskLevel.emoji} ${app.riskLevel.shortLabel}",
                style = MaterialTheme.typography.labelSmall,
                color = riskColor(app.riskLevel),
                fontWeight = FontWeight.SemiBold
            )
            // Tehlikeli izin sayısı
            if (app.dangerousPermissions.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(AlertRed.copy(alpha = 0.15f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        "${app.dangerousPermissions.size}",
                        style = MaterialTheme.typography.labelSmall,
                        color = AlertRed,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun RiskChip(label: String, color: androidx.compose.ui.graphics.Color) {
    Box(
        modifier = Modifier
            .background(color.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun riskColor(level: AppRiskLevel) = when (level) {
    AppRiskLevel.LOW    -> PrimaryGreen
    AppRiskLevel.MEDIUM -> AlertOrange
    AppRiskLevel.HIGH   -> AlertRed
}
