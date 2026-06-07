package io.privacydroid.ui.apps

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.privacydroid.data.local.dao.AppBlockingExceptionDao
import io.privacydroid.data.local.dao.PermissionLogDao
import io.privacydroid.data.local.entity.AppBlockingExceptionEntity
import io.privacydroid.data.source.AppInventoryScanner
import io.privacydroid.domain.model.AppInfo
import io.privacydroid.domain.model.AppRiskLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

/** Modül 5: Kritik izin filtre seçenekleri */
enum class CriticalPermissionFilter(
    val emoji: String,
    val label: String,
    val permissionName: String
) {
    WIFI_CHANGE("📶", "Wi-Fi Açma",      "android.permission.CHANGE_WIFI_STATE"),
    MOBILE_DATA("📱", "Mobil Veri",      "android.permission.CHANGE_NETWORK_STATE"),
    CAMERA(     "📷", "Kamera",           "android.permission.CAMERA"),
    MICROPHONE( "🎤", "Mikrofon",         "android.permission.RECORD_AUDIO"),
    CONTACTS(   "👥", "Rehber",           "android.permission.READ_CONTACTS"),
    FILE_ACCESS("💾", "Dosya Erişimi",    "android.permission.READ_EXTERNAL_STORAGE"),
    LOCATION(   "📍", "Konum",            "android.permission.ACCESS_FINE_LOCATION"),
    SMS(        "✉️", "SMS",              "android.permission.READ_SMS"),
    CALL_LOG(   "📞", "Arama Geçmişi",   "android.permission.READ_CALL_LOG")
}

data class AppsUiState(
    val isLoading: Boolean = true,
    val apps: List<AppInfo> = emptyList(),
    val filteredApps: List<AppInfo> = emptyList(),
    val searchQuery: String = "",
    val includeSystemApps: Boolean = false,
    val selectedApp: AppInfo? = null,
    val error: String? = null,
    // Modül 5
    val showCriticalFilterSheet: Boolean = false,
    val selectedCriticalFilters: Set<CriticalPermissionFilter> = emptySet(),
    val criticalFilterApps: List<AppInfo> = emptyList()
)

@HiltViewModel
class AppsViewModel @Inject constructor(
    private val scanner: AppInventoryScanner,
    private val permissionLogDao: PermissionLogDao,
    private val appBlockingExceptionDao: AppBlockingExceptionDao
) : ViewModel() {

    private val _permissionUsageCounts = MutableStateFlow<Map<String, Int>>(emptyMap())
    val permissionUsageCounts: StateFlow<Map<String, Int>> = _permissionUsageCounts.asStateFlow()

    /** Reklam engellemeden hariç tutulan paketler — liste 🔓 ikonu ve detay toggle'ı için. */
    val blockingExceptionPackages: StateFlow<Set<String>> =
        appBlockingExceptionDao.observePackageNames()
            .map { it.toHashSet() as Set<String> }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    /** Bir uygulama için engellemeyi aç/kapat. enabled=false → istisna ekle. */
    fun setAppBlockingEnabled(app: AppInfo, enabled: Boolean) {
        viewModelScope.launch {
            if (enabled) {
                appBlockingExceptionDao.delete(app.packageName)
            } else {
                appBlockingExceptionDao.insert(
                    AppBlockingExceptionEntity(packageName = app.packageName, appName = app.appName)
                )
            }
        }
    }

    private val _uiState = MutableStateFlow(AppsUiState())
    val uiState: StateFlow<AppsUiState> = _uiState.asStateFlow()

    init {
        loadApps()
    }

    fun loadApps() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            runCatching {
                withContext(Dispatchers.IO) {
                    scanner.scanAllApps(includeSystem = _uiState.value.includeSystemApps)
                        .sortedWith(
                            compareByDescending<AppInfo> { it.riskLevel.ordinal }
                                .thenByDescending { it.lastUpdateMs }
                        )
                }
            }.fold(
                onSuccess = { apps ->
                    _uiState.update { state ->
                        state.copy(
                            isLoading = false,
                            apps = apps,
                            filteredApps = apps.applyQuery(state.searchQuery),
                            criticalFilterApps = computeCriticalFilterApps(apps, state.selectedCriticalFilters)
                        )
                    }
                },
                onFailure = { e ->
                    _uiState.update { it.copy(isLoading = false, error = e.message) }
                }
            )
        }
    }

    fun setSearchQuery(query: String) {
        _uiState.update {
            it.copy(
                searchQuery = query,
                filteredApps = it.apps.applyQuery(query)
            )
        }
    }

    fun toggleSystemApps() {
        _uiState.update { it.copy(includeSystemApps = !it.includeSystemApps) }
        loadApps()
    }

    fun selectApp(app: AppInfo) {
        viewModelScope.launch {
            _uiState.update { it.copy(selectedApp = app) }
            // 30 günlük kullanım sayılarını yükle
            val sinceMs = System.currentTimeMillis() - java.util.concurrent.TimeUnit.DAYS.toMillis(30)
            val counts = permissionLogDao.getPermissionUsageForApp(app.packageName, sinceMs)
            _permissionUsageCounts.value = counts.associate { it.permission_type to it.count }
            // Arka planda derin tarama — tamamlanınca güncelle
            withContext(Dispatchers.IO) {
                scanner.scanDeep(app.packageName)
            }?.let { deepApp ->
                _uiState.update { state ->
                    state.copy(
                        selectedApp = deepApp,
                        apps = state.apps.map { if (it.packageName == deepApp.packageName) deepApp else it },
                        filteredApps = state.filteredApps.map { if (it.packageName == deepApp.packageName) deepApp else it }
                    )
                }
            }
        }
    }

    fun dismissDetail() {
        _uiState.update { it.copy(selectedApp = null) }
    }

    private fun List<AppInfo>.applyQuery(query: String): List<AppInfo> =
        if (query.isBlank()) this
        else filter {
            it.appName.contains(query, ignoreCase = true) ||
                    it.packageName.contains(query, ignoreCase = true)
        }

    /** Risk seviyesi sayıları — header istatistikleri için */
    fun riskCounts(): Triple<Int, Int, Int> {
        val apps = _uiState.value.filteredApps
        return Triple(
            apps.count { it.riskLevel == AppRiskLevel.HIGH },
            apps.count { it.riskLevel == AppRiskLevel.MEDIUM },
            apps.count { it.riskLevel == AppRiskLevel.LOW }
        )
    }

    // ── Modül 5: Kritik İzin Tarama Modu ─────────────────────────────────────

    fun toggleCriticalFilterSheet() {
        _uiState.update { it.copy(showCriticalFilterSheet = !it.showCriticalFilterSheet) }
    }

    fun toggleCriticalPermissionFilter(filter: CriticalPermissionFilter) {
        _uiState.update { state ->
            val newFilters = if (filter in state.selectedCriticalFilters) {
                state.selectedCriticalFilters - filter
            } else {
                state.selectedCriticalFilters + filter
            }
            state.copy(
                selectedCriticalFilters = newFilters,
                criticalFilterApps = computeCriticalFilterApps(state.apps, newFilters)
            )
        }
    }

    private fun computeCriticalFilterApps(
        apps: List<AppInfo>,
        filters: Set<CriticalPermissionFilter>
    ): List<AppInfo> {
        val allCriticalPerms = CriticalPermissionFilter.entries.map { it.permissionName }.toSet()
        return if (filters.isEmpty()) {
            apps.filter { app ->
                app.requestedPermissions.any { it in allCriticalPerms }
            }
        } else {
            apps.filter { app ->
                filters.all { filter -> filter.permissionName in app.requestedPermissions }
            }
        }.sortedByDescending { it.riskLevel.ordinal }
    }

    /** Seçili filtreye göre özet metin (BottomSheet sağ üst) */
    fun buildCriticalFilterSummary(): String {
        val state = _uiState.value
        val filters = state.selectedCriticalFilters
        val apps = state.criticalFilterApps
        if (filters.isEmpty()) {
            return "${apps.size} uygulama kritik izne sahip"
        }
        return filters.joinToString("\n") { f ->
            val count = apps.count { app -> f.permissionName in app.requestedPermissions }
            "${f.label}: $count uygulama"
        }
    }

    /** Share Sheet için metin raporu üretir */
    fun buildCriticalFilterReport(): String {
        val state = _uiState.value
        val filters = state.selectedCriticalFilters
        val apps = state.criticalFilterApps
        val dateStr = SimpleDateFormat("d MMMM yyyy", Locale("tr")).format(Date())
        val filterStr = if (filters.isEmpty()) "Tüm Kritik İzinler"
                        else filters.joinToString(" + ") { it.label }
        return buildString {
            appendLine("=== KRİTİK İZİN RAPORU ===")
            appendLine("Tarih: $dateStr")
            appendLine("Filtre: $filterStr")
            appendLine()
            apps.forEachIndexed { idx, app ->
                appendLine("${idx + 1}. ${app.appName} (${app.packageName})")
                appendLine("   Risk: ${app.riskLevel.name}")
                val matchedFilters = if (filters.isEmpty()) {
                    CriticalPermissionFilter.entries.filter { f ->
                        f.permissionName in app.requestedPermissions
                    }
                } else {
                    filters.filter { f -> f.permissionName in app.requestedPermissions }
                }
                matchedFilters.forEach { f -> appendLine("   ${f.emoji} ${f.label}") }
                appendLine()
            }
        }
    }

    fun shareCriticalReport(context: Context) {
        val report = buildCriticalFilterReport()
        val intent = Intent.createChooser(
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, report)
                putExtra(Intent.EXTRA_SUBJECT, "PrivacyDroid Kritik İzin Raporu")
            },
            "Raporu Paylaş"
        ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        context.startActivity(intent)
    }
}
