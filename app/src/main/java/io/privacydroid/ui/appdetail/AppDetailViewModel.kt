package io.privacydroid.ui.appdetail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.map
import dagger.hilt.android.lifecycle.HiltViewModel
import io.privacydroid.data.local.dao.AppBlockingExceptionDao
import io.privacydroid.data.local.dao.AppPermissionSnapshotDao
import io.privacydroid.data.local.dao.CorrelationResultDao
import io.privacydroid.data.local.dao.PermissionLogDao
import io.privacydroid.data.local.dao.TrackerConnectionDao
import io.privacydroid.data.local.dao.TrackerDomainSummary
import io.privacydroid.data.local.entity.AppBlockingExceptionEntity
import io.privacydroid.data.local.entity.AppPermissionSnapshotEntity
import io.privacydroid.data.local.entity.CorrelationResultEntity
import io.privacydroid.data.model.PermissionLog
import io.privacydroid.domain.model.AppDetail
import io.privacydroid.domain.usecase.AppReportData
import io.privacydroid.domain.usecase.ReportGenerator
import io.privacydroid.domain.usecase.RiskScoreCalculator
import io.privacydroid.domain.repository.PermissionRepository
import io.privacydroid.ui.dashboard.permissionColor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import javax.inject.Inject

data class LogRowItem(
    val id: Long,
    val permissionLabel: String,
    val permissionColor: androidx.compose.ui.graphics.Color,
    val isBackground: Boolean,
    val isNight: Boolean,
    val timeLabel: String,
    val durationLabel: String,
    val accessTimeMs: Long
)

data class AppDetailUiState(
    val isLoading: Boolean = true,
    val appDetail: AppDetail? = null,
    val error: String? = null
)

@HiltViewModel
class AppDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: PermissionRepository,
    private val riskScoreCalculator: RiskScoreCalculator,
    private val correlationDao: CorrelationResultDao,
    private val snapshotDao: AppPermissionSnapshotDao,
    private val permissionLogDao: PermissionLogDao,
    private val trackerConnectionDao: TrackerConnectionDao,
    private val appBlockingExceptionDao: AppBlockingExceptionDao,
    private val reportGenerator: ReportGenerator
) : ViewModel() {

    private val packageName: String = checkNotNull(savedStateHandle["packageName"])

    /**
     * Bu uygulama engellemeden HARİÇ mi? (true = engelleme bu uygulama için KAPALI)
     * Toggle "Reklam Engelleme" açık = !isBlockingException.
     */
    val isBlockingException: StateFlow<Boolean> =
        appBlockingExceptionDao.observeIsException(packageName)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /**
     * Bu uygulama için engellemeyi aç/kapat.
     * enabled=false → istisna listesine ekle (engelleme kapalı, tüm domainlere erişebilir)
     * enabled=true  → istisnadan çıkar (engelleme tekrar aktif)
     */
    fun setAppBlockingEnabled(enabled: Boolean) {
        viewModelScope.launch {
            if (enabled) {
                appBlockingExceptionDao.delete(packageName)
            } else {
                val appName = _uiState.value.appDetail?.appName ?: packageName
                appBlockingExceptionDao.insert(
                    AppBlockingExceptionEntity(packageName = packageName, appName = appName)
                )
            }
        }
    }

    private val _uiState = MutableStateFlow(AppDetailUiState())
    val uiState: StateFlow<AppDetailUiState> = _uiState.asStateFlow()

    /** Son korelasyon sonucu — DB değişince otomatik güncellenir */
    val latestCorrelation: StateFlow<CorrelationResultEntity?> =
        correlationDao.getLatestForApp(packageName)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Son 30 günde her izin türü kaç kez kullanıldı — AppDetailSheet için */
    private val _permissionUsageCounts = MutableStateFlow<Map<String, Int>>(emptyMap())
    val permissionUsageCounts: StateFlow<Map<String, Int>> = _permissionUsageCounts.asStateFlow()

    /** İzin değişim geçmişi — snapshot tablosundan */
    private val _permissionHistory = MutableStateFlow<List<AppPermissionSnapshotEntity>>(emptyList())
    val permissionHistory: StateFlow<List<AppPermissionSnapshotEntity>> = _permissionHistory.asStateFlow()

    /** Son 7 günde bu uygulamanın bağlandığı tracker domainleri */
    private val _trackerDomains = MutableStateFlow<List<TrackerDomainSummary>>(emptyList())
    val trackerDomains: StateFlow<List<TrackerDomainSummary>> = _trackerDomains.asStateFlow()

    /** Paged log listesi — LazyPagingItems ile tüketilir */
    val pagedLogs: Flow<PagingData<LogRowItem>> = repository
        .getPagedLogsForApp(packageName)
        .map { pagingData ->
            pagingData.map { log -> log.toRowItem() }
        }
        .cachedIn(viewModelScope)

    /**
     * Faz 3 rapor motoru için toplanan veri. Detay yüklendikten sonra arka planda
     * doldurulur; paylaş/PDF/KVKK butonları bu önbelleği kullanır.
     */
    private val _reportData = MutableStateFlow<AppReportData?>(null)
    val reportData: StateFlow<AppReportData?> = _reportData.asStateFlow()

    init {
        loadDetail()
        loadPermissionUsage()
        loadPermissionHistory()
        loadTrackerDomains()
    }

    private fun loadTrackerDomains() {
        viewModelScope.launch {
            val sevenDaysAgo = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(7)
            _trackerDomains.value = trackerConnectionDao.getDomainSummaryForApp(packageName, sevenDaysAgo)
        }
    }

    private fun loadPermissionUsage() {
        viewModelScope.launch {
            val sinceMs = System.currentTimeMillis() - java.util.concurrent.TimeUnit.DAYS.toMillis(30)
            val counts = permissionLogDao.getPermissionUsageForApp(packageName, sinceMs)
            _permissionUsageCounts.value = counts.associate { it.permission_type to it.count }
        }
    }

    private fun loadPermissionHistory() {
        viewModelScope.launch {
            _permissionHistory.value = snapshotDao.getHistoryForApp(packageName)
        }
    }

    private fun loadDetail() {
        viewModelScope.launch {
            _uiState.value = AppDetailUiState(isLoading = true)
            runCatching {
                val sevenDaysAgo = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(7)
                val logs = repository.getLogsForAppSince(packageName, sevenDaysAgo)
                riskScoreCalculator.calculate(logs, packageName)
            }.fold(
                onSuccess = { detail ->
                    _uiState.value = AppDetailUiState(isLoading = false, appDetail = detail)
                    gatherReportData(detail)
                },
                onFailure = { e ->
                    _uiState.value = AppDetailUiState(isLoading = false, error = e.message)
                }
            )
        }
    }

    /** Detay yüklendikten sonra rapor verisini arka planda toplar. */
    private fun gatherReportData(detail: AppDetail) {
        viewModelScope.launch {
            runCatching {
                reportGenerator.gatherAppReport(
                    packageName = detail.packageName,
                    appName = detail.appName,
                    riskScore = detail.riskScore
                )
            }.onSuccess { _reportData.value = it }
        }
    }

    /** Paylaşılabilir metin rapor (Faz 3). Veri henüz hazır değilse özet döner. */
    fun buildReportText(detail: AppDetail): String {
        val data = _reportData.value
        return if (data != null) reportGenerator.buildAppReportText(data)
        else buildShareText(detail)
    }

    /** KVKK başvuru şablonu — veri hazır değilse null. */
    fun buildKvkkText(): String? =
        _reportData.value?.let { reportGenerator.buildKvkkTemplate(it) }

    fun buildShareText(detail: AppDetail): String {
        val lines = mutableListOf<String>()
        lines += "${detail.appName} Gizlilik Raporu — PrivacyDroid"
        lines += "Tarih: ${SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).apply { timeZone = TimeZone.getDefault() }.format(Date())}"
        lines += ""
        lines += "Son 7 Gün:"
        lines += "  Toplam erişim: ${detail.totalWeeklyCount}"
        lines += "  Arka plan erişimi: ${detail.backgroundCount}"
        lines += "  Gece erişimi (00-06): ${detail.nightCount}"
        lines += "  Farklı izin türü: ${detail.uniquePermissionCount}"
        lines += ""
        lines += "Risk Skoru: ${detail.riskScore}/100 (${riskLabel(detail.riskScore)})"
        lines += ""
        lines += "PrivacyDroid ile oluşturuldu"
        return lines.joinToString("\n")
    }

    private fun riskLabel(score: Int) = when {
        score <= 30 -> "Düşük Risk"
        score <= 60 -> "Orta Risk"
        else -> "Yüksek Risk"
    }
}

private fun PermissionLog.toRowItem(): LogRowItem {
    val cal = java.util.Calendar.getInstance().apply { timeInMillis = accessTime }
    val hour = cal.get(java.util.Calendar.HOUR_OF_DAY)
    return LogRowItem(
        id = id,
        permissionLabel = permissionType.displayName,
        permissionColor = permissionColor(permissionType.name),
        isBackground = isBackground,
        isNight = hour < 6,
        timeLabel = SimpleDateFormat("dd MMM HH:mm", Locale.getDefault()).format(Date(accessTime)),
        durationLabel = if (durationMs > 0) formatDuration(durationMs) else "",
        accessTimeMs = accessTime
    )
}

private fun formatDuration(ms: Long): String {
    val seconds = ms / 1000
    return if (seconds < 60) "${seconds}s" else "${seconds / 60}dk ${seconds % 60}s"
}
