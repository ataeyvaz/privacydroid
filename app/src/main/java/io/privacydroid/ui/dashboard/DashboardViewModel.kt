package io.privacydroid.ui.dashboard

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.privacydroid.data.local.dao.BlockedDomainDao
import io.privacydroid.data.local.dao.NotificationLogDao
import io.privacydroid.data.local.dao.TrackerConnectionDao
import io.privacydroid.data.local.dao.TrackerDashboardItem
import io.privacydroid.data.source.BlockingStats
import io.privacydroid.data.source.NetworkUsageTracker
import io.privacydroid.data.model.MonitoringMode
import io.privacydroid.data.repository.SettingsRepository
import io.privacydroid.domain.model.AppNetworkStats
import io.privacydroid.domain.model.DashboardStats
import io.privacydroid.domain.model.TrackerCategory
import io.privacydroid.domain.usecase.GetDashboardStatsUseCase
import io.privacydroid.domain.repository.PermissionRepository
import io.privacydroid.util.PermissionHelper
import io.privacydroid.ui.theme.ColorCamera
import io.privacydroid.ui.theme.ColorContacts
import io.privacydroid.ui.theme.ColorGeneric
import io.privacydroid.ui.theme.ColorLocation
import io.privacydroid.ui.theme.ColorMicrophone
import io.privacydroid.worker.WorkManagerHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import javax.inject.Inject

data class TopSenderItem(
    val packageName: String,
    val appName: String,
    val txBytes24h: Long,
    val isAnomaly: Boolean
)

data class BlockingStatsUi(
    val adsBlocked: Int = 0,
    val trackersBlocked: Int = 0,
    val allowed: Int = 0
) {
    val isActive: Boolean get() = adsBlocked > 0 || trackersBlocked > 0 || allowed > 0
}

data class TrackerItem(
    val appName: String,
    val packageName: String,
    val domain: String,
    val category: TrackerCategory,
    val connectionCount: Int,
    val totalBytes: Long,
    val lastSeenLabel: String
)

data class DashboardUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val stats: DashboardStats = DashboardStats(),
    val recentAccessItems: List<AccessLogItem> = emptyList(),
    val topSenders: List<TopSenderItem> = emptyList(),
    val trackerItems: List<TrackerItem> = emptyList(),
    val hasData: Boolean = false,
    val error: String? = null
)

data class AccessLogItem(
    val id: Long,                  // Room primary key — kesinlikle benzersiz
    val packageName: String,
    val appName: String,
    val permissionLabel: String,
    val permissionColor: Color,
    val isBackground: Boolean,
    val timeLabel: String,
    val accessTimeMs: Long
)

data class MonitoringStatusUiState(
    val modeLabel: String = "Periyodik",
    val isServiceRunning: Boolean = false,
    val lastScanLabel: String = "Henüz taranmadı",
    val nextScanLabel: String = "~15 dk sonra",
    val isTrackerEnabled: Boolean = true,
    val isVpnModeEnabled: Boolean = false
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val getDashboardStatsUseCase: GetDashboardStatsUseCase,
    private val repository: PermissionRepository,
    private val workManagerHelper: WorkManagerHelper,
    private val permissionHelper: PermissionHelper,
    private val networkUsageTracker: NetworkUsageTracker,
    private val trackerConnectionDao: TrackerConnectionDao,
    private val settingsRepository: SettingsRepository,
    private val notificationLogDao: NotificationLogDao,
    private val blockedDomainDao: BlockedDomainDao,
    private val blockingStats: BlockingStats
) : ViewModel() {

    private val todayStartMs: Long = java.util.Calendar.getInstance().apply {
        set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0)
        set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0)
    }.timeInMillis

    /** "🛡️ Engelleme İstatistikleri" kartı için bugünkü sayılar. */
    val blockingStatsUi: StateFlow<BlockingStatsUi> = combine(
        blockedDomainDao.countByTypeSince("AD", todayStartMs),
        blockedDomainDao.countByTypeSince("TRACKER", todayStartMs),
        blockingStats.allowedToday
    ) { ads, trackers, allowed -> BlockingStatsUi(ads, trackers, allowed) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BlockingStatsUi())

    /**
     * İki Flow combine() ile birleştirilir:
     *   1. getDashboardStatsUseCase.observe() → bugünkü istatistikler
     *   2. repository.getRecentLogs()         → son 50 erişim kaydı
     *
     * DB her değiştiğinde her iki Flow da tetiklenir, UI otomatik güncellenir.
     */
    private val since24h get() = System.currentTimeMillis() - java.util.concurrent.TimeUnit.HOURS.toMillis(24)

    val uiState: StateFlow<DashboardUiState> = combine(
        getDashboardStatsUseCase.observe(),
        repository.getRecentLogs(50),
        trackerConnectionDao.getDashboardSummary(sinceMs = since24h)
    ) { stats, logs, trackers ->
        DashboardUiState(
            isLoading = false,
            stats = stats,
            recentAccessItems = logs.map { log ->
                AccessLogItem(
                    id = log.id,
                    packageName = log.packageName,
                    appName = log.appName,
                    permissionLabel = log.permissionType.displayName,
                    permissionColor = permissionColor(log.permissionType.name),
                    isBackground = log.isBackground,
                    timeLabel = formatTime(log.accessTime),
                    accessTimeMs = log.accessTime
                )
            },
            hasData = stats.totalCount > 0 || logs.isNotEmpty(),
            topSenders = buildTopSenders(logs),
            trackerItems = trackers.map { it.toTrackerItem() },
            error = null
        )
    }
        .catch { e -> emit(DashboardUiState(isLoading = false, error = e.message)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardUiState())

    private val _hasPermission = MutableStateFlow(permissionHelper.hasUsageStatsPermission())
    val hasPermission: StateFlow<Boolean> = _hasPermission.asStateFlow()

    /** Modül 4: İzleme durumu kartı için live state. */
    val monitoringStatus: StateFlow<MonitoringStatusUiState> = combine(
        settingsRepository.observeMonitoringMode(),
        settingsRepository.observeTrackerMonitoringEnabled(),
        settingsRepository.observeVpnModeEnabled(),
        settingsRepository.observeLastPermissionScanMs()
    ) { mode, trackerEnabled, vpnEnabled, lastScanMs ->
        val intervalMs = if (mode == MonitoringMode.REALTIME)
            2L * 60 * 1000 else 15L * 60 * 1000
        val nextScanMs = lastScanMs + intervalMs
        val nextLabel = if (lastScanMs == 0L) "Bekleniyor" else {
            val remaining = (nextScanMs - System.currentTimeMillis()) / 1000 / 60
            if (remaining <= 0) "Az sonra" else "~${remaining} dk sonra"
        }
        MonitoringStatusUiState(
            modeLabel = if (mode == MonitoringMode.REALTIME) "Gerçek Zamanlı" else "Periyodik",
            isServiceRunning = mode == MonitoringMode.REALTIME,
            lastScanLabel = formatLastScanTime(lastScanMs),
            nextScanLabel = nextLabel,
            isTrackerEnabled = trackerEnabled,
            isVpnModeEnabled = vpnEnabled
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        MonitoringStatusUiState()
    )

    /** Okunmamış bildirim sayısı — dashboard kartı ve badge için */
    val unreadNotificationCount: StateFlow<Int> = notificationLogDao.getUnreadCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    fun checkPermissionOnResume() {
        _hasPermission.value = permissionHelper.hasUsageStatsPermission()
    }

    /** Pull-to-refresh: anlık WorkManager taraması başlatır. */
    fun refresh() {
        workManagerHelper.triggerImmediateScan()
    }

    private fun TrackerDashboardItem.toTrackerItem() = TrackerItem(
        appName = app_name,
        packageName = package_name,
        domain = domain,
        category = TrackerCategory.fromString(category),
        connectionCount = connectionCount,
        totalBytes = totalBytes,
        lastSeenLabel = formatLastScanTime(lastSeen)
    )

    private fun buildTopSenders(
        logs: List<io.privacydroid.data.model.PermissionLog>
    ): List<TopSenderItem> {
        // Log sahibi uygulamaların UID'lerini tahmin et (TrafficStats UID gerektirir)
        // Basit yaklaşım: unique package'lar üzerinden bilgi göster
        return logs.groupBy { it.packageName }
            .entries
            .take(5)
            .map { (pkg, pkgLogs) ->
                TopSenderItem(
                    packageName = pkg,
                    appName = pkgLogs.first().appName,
                    txBytes24h = 0L, // NetworkUsageTracker UID gerektiriyor, ileride entegre edilecek
                    isAnomaly = false
                )
            }
    }

    private fun formatTime(timeMs: Long): String =
        SimpleDateFormat("HH:mm", Locale.getDefault())
            .apply { timeZone = TimeZone.getDefault() }
            .format(Date(timeMs))
}

fun permissionColor(permissionTypeName: String): Color = when (permissionTypeName) {
    "CAMERA" -> ColorCamera
    "MICROPHONE" -> ColorMicrophone
    "LOCATION_FINE", "LOCATION_COARSE" -> ColorLocation
    "CONTACTS" -> ColorContacts
    else -> ColorGeneric
}

fun formatLastScanTime(lastScanMs: Long): String {
    if (lastScanMs == 0L) return "Henüz taranmadı"
    val diffMs = System.currentTimeMillis() - lastScanMs
    return when {
        diffMs < TimeUnit.MINUTES.toMillis(1) -> "Az önce"
        diffMs < TimeUnit.HOURS.toMillis(1) -> "${TimeUnit.MILLISECONDS.toMinutes(diffMs)} dk önce"
        diffMs < TimeUnit.DAYS.toMillis(1) -> "${TimeUnit.MILLISECONDS.toHours(diffMs)} sa önce"
        else -> "${TimeUnit.MILLISECONDS.toDays(diffMs)} gün önce"
    }
}
