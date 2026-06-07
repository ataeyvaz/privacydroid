package io.privacydroid.ui.debug

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.privacydroid.ui.theme.AlertOrange
import io.privacydroid.ui.theme.AlertRed
import io.privacydroid.ui.theme.DarkSurfaceVariant
import io.privacydroid.ui.theme.PrimaryGreen

/**
 * Yalnızca DEBUG build'de erişilebilir.
 * Bildirim sistemi, WorkManager ve scan motorunu manuel test etmek için araçlar içerir.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugScreen(
    onBack: () -> Unit,
    viewModel: DebugViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Outlined.BugReport, null, tint = AlertOrange)
                        Text("Debug Araçları")
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Geri")
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
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            DebugSection("Bildirim Testleri") {
                DebugButton(
                    label = "Gece Erişimi Bildirimi",
                    color = AlertRed,
                    onClick = viewModel::sendNightAccessNotification
                )
                DebugButton(
                    label = "Arka Plan Sensör Bildirimi",
                    color = AlertOrange,
                    onClick = viewModel::sendBackgroundSensorNotification
                )
                DebugButton(
                    label = "Konum Burst Bildirimi",
                    color = AlertOrange,
                    onClick = viewModel::sendLocationBurstNotification
                )
                DebugButton(
                    label = "Günlük Özet Bildirimi",
                    color = PrimaryGreen,
                    onClick = viewModel::sendDailySummaryNotification
                )
                DebugButton(
                    label = "Çoklu Bildirim (3+ uygulama)",
                    color = AlertRed,
                    onClick = viewModel::sendMultipleNotifications
                )
            }

            DebugSection("Scan Motoru") {
                DebugButton(
                    label = "Manuel Tarama Başlat",
                    color = PrimaryGreen,
                    onClick = viewModel::triggerManualScan
                )
                DebugButton(
                    label = "Throttle Sıfırla",
                    color = AlertOrange,
                    onClick = viewModel::resetNotificationThrottle
                )
            }

            DebugSection("WorkManager") {
                DebugButton(
                    label = "Periyodik İşi Yeniden Planla",
                    color = PrimaryGreen,
                    onClick = viewModel::reschedulePeriodic
                )
                DebugButton(
                    label = "Tüm İşleri İptal Et",
                    color = AlertRed,
                    onClick = viewModel::cancelAllWork
                )
            }

            // Son işlem çıktısı
            if (uiState.lastActionResult.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(DarkSurfaceVariant, RoundedCornerShape(8.dp))
                        .padding(12.dp)
                ) {
                    Text(
                        text = "Son İşlem",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = uiState.lastActionResult,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace
                        ),
                        color = PrimaryGreen
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun DebugSection(title: String, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(DarkSurfaceVariant, RoundedCornerShape(12.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        content()
    }
}

@Composable
private fun DebugButton(
    label: String,
    color: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = color)
    ) {
        Text(label)
    }
}
