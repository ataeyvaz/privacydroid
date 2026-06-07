package io.privacydroid.ui.settings

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.privacydroid.data.local.dao.BlockedDomainDao
import io.privacydroid.data.model.BlockingMode
import io.privacydroid.data.model.MonitoringMode
import io.privacydroid.data.model.NotificationSensitivity
import io.privacydroid.data.repository.CrashLogRepository
import io.privacydroid.data.repository.SettingsRepository
import io.privacydroid.data.source.BlockingStats
import io.privacydroid.domain.usecase.ReportGenerator
import io.privacydroid.util.ReportExporter
import android.net.Uri
import io.privacydroid.service.PermissionMonitorService
import io.privacydroid.util.CrashLogParser
import io.privacydroid.util.ParsedCrashSummary
import io.privacydroid.worker.WorkManagerHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

enum class ScanState { IDLE, SCANNING, COMPLETED, ERROR }

data class SettingsUiState(
    val monitoringMode: MonitoringMode = MonitoringMode.PERIODIC,
    val suspiciousNotificationsEnabled: Boolean = true,
    val summaryNotificationsEnabled: Boolean = false,
    val notificationSensitivity: NotificationSensitivity = NotificationSensitivity.MEDIUM,
    val trackerMonitoringEnabled: Boolean = true,
    val lastTrackerScanLabel: String = "Henüz taranmadı",
    val vpnModeEnabled: Boolean = false,
    // DNS engelleme
    val blockingMode: BlockingMode = BlockingMode.OFF,
    val adsBlockedToday: Int = 0,
    val trackersBlockedToday: Int = 0,
    val allowedToday: Int = 0,
    val scanState: ScanState = ScanState.IDLE,
    val scanMessage: String = "",
    // Crash log bölümü
    val crashLogCount: Int = 0,
    val crashLogFiles: List<CrashLogEntry> = emptyList(),
    // Seçili log dialogu
    val selectedEntry: CrashLogEntry? = null,
    val showTechnicalDetail: Boolean = false,
    val crashLogsCleared: Boolean = false
)

data class CrashLogEntry(
    val file: File,
    val displayName: String,   // "5 Haz 2026 — 03:24"
    val sizeKb: Int,
    val summary: ParsedCrashSummary,  // Kullanıcı dostu özet
    val rawContent: String            // Ham stack trace
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val workManagerHelper: WorkManagerHelper,
    private val crashLogRepository: CrashLogRepository,
    private val blockedDomainDao: BlockedDomainDao,
    private val blockingStats: BlockingStats,
    private val reportGenerator: ReportGenerator
) : ViewModel() {

    // ── Faz 3 Modül 4: Cihaz geneli rapor ────────────────────────────────────

    data class DeviceReportState(
        val isGenerating: Boolean = false,
        val text: String? = null
    )

    private val _deviceReport = MutableStateFlow(DeviceReportState())
    val deviceReport: StateFlow<DeviceReportState> = _deviceReport

    /** Tüm uygulamaları kapsayan genel raporu oluşturur (arka planda). */
    fun generateDeviceReport() {
        if (_deviceReport.value.isGenerating) return
        _deviceReport.value = DeviceReportState(isGenerating = true)
        viewModelScope.launch {
            runCatching {
                val data = reportGenerator.gatherDeviceReport()
                reportGenerator.buildDeviceReportText(data)
            }.fold(
                onSuccess = { _deviceReport.value = DeviceReportState(text = it) },
                onFailure = {
                    _deviceReport.value = DeviceReportState(
                        text = "Rapor oluşturulamadı: ${it.message}"
                    )
                }
            )
        }
    }

    fun dismissDeviceReport() { _deviceReport.value = DeviceReportState() }

    fun shareDeviceReport() {
        _deviceReport.value.text?.let {
            ReportExporter.shareText(context, it, "PrivacyDroid Cihaz Gizlilik Raporu")
        }
    }

    fun copyDeviceReport() {
        _deviceReport.value.text?.let {
            ReportExporter.copyToClipboard(context, "Cihaz Gizlilik Raporu", it)
        }
    }

    /** Cihaz raporunu PDF olarak kaydeder; başarılıysa Uri döner. */
    fun saveDeviceReportPdf(): Uri? {
        val text = _deviceReport.value.text ?: return null
        val stamp = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US).format(java.util.Date())
        val pretty = java.text.SimpleDateFormat("d MMMM yyyy", java.util.Locale("tr")).format(java.util.Date())
        return ReportExporter.savePdf(
            context = context,
            fileName = "cihaz_raporu_$stamp.pdf",
            title = "Cihaz Gizlilik Raporu",
            subtitle = "PrivacyDroid · $pretty",
            body = text
        )
    }

    /** Bugünün başlangıcı (yerel) — engelleme istatistik sorguları için. */
    private val todayStartMs: Long = java.util.Calendar.getInstance().apply {
        set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0)
        set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0)
    }.timeInMillis

    fun setBlockingMode(mode: BlockingMode) {
        settingsRepository.setBlockingMode(mode)
    }

    private data class BlockingUi(
        val mode: BlockingMode,
        val ads: Int,
        val trackers: Int,
        val allowed: Int
    )

    private val _blockingState = combine(
        settingsRepository.observeBlockingMode(),
        blockedDomainDao.countByTypeSince("AD", todayStartMs),
        blockedDomainDao.countByTypeSince("TRACKER", todayStartMs),
        blockingStats.allowedToday
    ) { mode, ads, trackers, allowed -> BlockingUi(mode, ads, trackers, allowed) }

    private val _scanState = MutableStateFlow(ScanState.IDLE)
    private val _scanMessage = MutableStateFlow("")

    fun setTrackerMonitoringEnabled(enabled: Boolean) {
        settingsRepository.setTrackerMonitoringEnabled(enabled)
    }

    fun triggerManualTrackerScan() {
        if (_scanState.value == ScanState.SCANNING) return

        _scanState.value = ScanState.SCANNING
        _scanMessage.value = "Tracker bağlantıları taranıyor..."

        val workId = workManagerHelper.triggerTrackerScan()

        viewModelScope.launch {
            try {
                val result = workManagerHelper.observeWork(workId)
                    .first { it.state.isFinished }
                if (result.state == WorkInfo.State.SUCCEEDED) {
                    _scanState.value = ScanState.COMPLETED
                    _scanMessage.value = "Tracker taraması tamamlandı"
                } else {
                    _scanState.value = ScanState.ERROR
                    _scanMessage.value = "Tracker taraması tamamlanamadı. Otomatik tarama 15 dakika içinde tekrar deneyecek."
                }
            } catch (e: Exception) {
                _scanState.value = ScanState.ERROR
                _scanMessage.value = "Tracker taraması tamamlanamadı. Otomatik tarama 15 dakika içinde tekrar deneyecek."
            }
            delay(3_000)
            _scanState.value = ScanState.IDLE
            _scanMessage.value = ""
        }
    }

    fun setVpnModeEnabled(enabled: Boolean) {
        settingsRepository.setVpnModeEnabled(enabled)
        try {
            if (enabled) {
                io.privacydroid.service.LocalVpnService.startVpn(context)
            } else {
                io.privacydroid.service.LocalVpnService.stopVpn(context)
            }
        } catch (e: Exception) {
            timber.log.Timber.e(e, "VPN servisi başlatılamadı/durdurulamadı")
        }
    }

    private val _crashState = MutableStateFlow(loadCrashLogState())

    private val _coreState = combine(
        settingsRepository.observeMonitoringMode(),
        settingsRepository.observeTrackerMonitoringEnabled(),
        settingsRepository.observeVpnModeEnabled(),
        settingsRepository.observeNotificationSensitivity(),
        _crashState
    ) { mode, trackerEnabled, vpnEnabled, sensitivity, crash ->
        SettingsUiState(
            monitoringMode = mode,
            suspiciousNotificationsEnabled = settingsRepository.isSuspiciousNotificationsEnabled(),
            summaryNotificationsEnabled = settingsRepository.isSummaryNotificationsEnabled(),
            notificationSensitivity = sensitivity,
            trackerMonitoringEnabled = trackerEnabled,
            lastTrackerScanLabel = io.privacydroid.ui.dashboard.formatLastScanTime(
                settingsRepository.getLastPermissionScanMs()
            ),
            vpnModeEnabled = vpnEnabled,
            crashLogCount = crash.crashLogCount,
            crashLogFiles = crash.crashLogFiles,
            selectedEntry = crash.selectedEntry,
            showTechnicalDetail = crash.showTechnicalDetail,
            crashLogsCleared = crash.crashLogsCleared
        )
    }

    /**
     * "Son tarama" etiketi için: permission scan ve tracker scan zamanlarından
     * en güncelini izler. İki kaynaktan biri her değiştiğinde yeniden hesaplanır.
     */
    private val lastScanMs: kotlinx.coroutines.flow.Flow<Long> = combine(
        settingsRepository.observeLastPermissionScanMs(),
        settingsRepository.observeLastTrackerScanMs()
    ) { permScanMs, trackerScanMs -> maxOf(permScanMs, trackerScanMs) }

    val uiState: StateFlow<SettingsUiState> = combine(
        _coreState,
        _scanState,
        _scanMessage,
        lastScanMs,
        _blockingState
    ) { core, scanState, scanMessage, scanMs, blocking ->
        core.copy(
            scanState = scanState,
            scanMessage = scanMessage,
            lastTrackerScanLabel = io.privacydroid.ui.dashboard.formatLastScanTime(scanMs),
            blockingMode = blocking.mode,
            adsBlockedToday = blocking.ads,
            trackersBlockedToday = blocking.trackers,
            allowedToday = blocking.allowed
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    // ---- Monitoring mode ----

    fun setMonitoringMode(mode: MonitoringMode) {
        viewModelScope.launch {
            settingsRepository.setMonitoringMode(mode)
            applyModeChange(mode)
        }
    }

    fun setSuspiciousNotificationsEnabled(enabled: Boolean) {
        settingsRepository.setSuspiciousNotificationsEnabled(enabled)
    }

    fun setSummaryNotificationsEnabled(enabled: Boolean) {
        settingsRepository.setSummaryNotificationsEnabled(enabled)
    }

    fun setNotificationSensitivity(sensitivity: NotificationSensitivity) {
        settingsRepository.setNotificationSensitivity(sensitivity)
    }

    // ---- Crash logs ----

    fun viewCrashLog(entry: CrashLogEntry) {
        _crashState.value = _crashState.value.copy(
            selectedEntry = entry,
            showTechnicalDetail = false  // Her açılışta özet katmanından başla
        )
    }

    fun toggleTechnicalDetail() {
        _crashState.value = _crashState.value.copy(
            showTechnicalDetail = !_crashState.value.showTechnicalDetail
        )
    }

    fun dismissCrashLogView() {
        _crashState.value = _crashState.value.copy(
            selectedEntry = null,
            showTechnicalDetail = false
        )
    }

    fun shareCrashLog(entry: CrashLogEntry) {
        if (entry.rawContent.isBlank()) return
        val intent = Intent.createChooser(
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, entry.rawContent)
                putExtra(Intent.EXTRA_SUBJECT, "PrivacyDroid Hata: ${entry.summary.timestamp}")
            },
            "Hata Günlüğünü Paylaş"
        ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        context.startActivity(intent)
    }

    fun shareLatestCrashLog() {
        _crashState.value.crashLogFiles.firstOrNull()?.let { shareCrashLog(it) }
    }

    fun clearCrashLogs() {
        crashLogRepository.deleteAllCrashLogs()
        _crashState.value = CrashLogStatePartial(
            crashLogCount = 0,
            crashLogFiles = emptyList(),
            selectedEntry = null,
            showTechnicalDetail = false,
            crashLogsCleared = true
        )
    }

    // ---- Private helpers ----

    private fun loadCrashLogState(): CrashLogStatePartial {
        val files = crashLogRepository.listCrashLogs()
        return CrashLogStatePartial(
            crashLogCount = files.size,
            crashLogFiles = files.map { f ->
                val raw = crashLogRepository.readCrashLog(f)
                val summary = CrashLogParser.parse(f.name, raw)
                CrashLogEntry(
                    file = f,
                    displayName = summary.timestamp,
                    sizeKb = (f.length() / 1024).toInt().coerceAtLeast(1),
                    summary = summary,
                    rawContent = raw
                )
            },
            selectedEntry = null,
            showTechnicalDetail = false,
            crashLogsCleared = false
        )
    }

    private fun applyModeChange(mode: MonitoringMode) {
        when (mode) {
            MonitoringMode.PERIODIC -> {
                stopRealtimeService()
                workManagerHelper.schedulePeriodic(androidx.work.ExistingPeriodicWorkPolicy.UPDATE)
            }
            MonitoringMode.REALTIME -> {
                workManagerHelper.cancelPeriodic()
                startRealtimeService()
            }
        }
    }

    private fun startRealtimeService() {
        context.startForegroundService(
            Intent(context, PermissionMonitorService::class.java)
                .apply { setAction(PermissionMonitorService.ACTION_START) }
        )
    }

    private fun stopRealtimeService() {
        context.startService(
            Intent(context, PermissionMonitorService::class.java)
                .apply { setAction(PermissionMonitorService.ACTION_STOP) }
        )
    }
}

private data class CrashLogStatePartial(
    val crashLogCount: Int,
    val crashLogFiles: List<CrashLogEntry>,
    val selectedEntry: CrashLogEntry?,
    val showTechnicalDetail: Boolean,
    val crashLogsCleared: Boolean
)
