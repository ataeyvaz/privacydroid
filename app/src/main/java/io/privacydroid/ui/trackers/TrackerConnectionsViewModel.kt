package io.privacydroid.ui.trackers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.privacydroid.data.local.dao.TrackerConnectionDao
import io.privacydroid.data.local.dao.TrackerFullItem
import io.privacydroid.data.repository.SettingsRepository
import io.privacydroid.domain.model.TrackerCategory
import io.privacydroid.ui.common.TimeRange
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** Tam tracker listesi ekranındaki üst filtre kategorileri. */
enum class TrackerFilterCategory(val label: String) {
    ALL("Hepsi"),
    ADS("Reklam"),
    TRACKER("Tracker"),
    CDN("CDN")
}

data class TrackerRowUi(
    val packageName: String,
    val appName: String,
    val domain: String,
    val category: TrackerCategory,
    val connectionCount: Int,
    val totalBytes: Long,
    val firstSeenMs: Long,
    val lastSeenMs: Long,
    val dnsVerified: Boolean
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TrackerConnectionsViewModel @Inject constructor(
    private val trackerConnectionDao: TrackerConnectionDao,
    settingsRepository: SettingsRepository
) : ViewModel() {

    private val _timeRange = MutableStateFlow(TimeRange.WEEK)
    val timeRange: StateFlow<TimeRange> = _timeRange.asStateFlow()

    private val _categoryFilter = MutableStateFlow(TrackerFilterCategory.ALL)
    val categoryFilter: StateFlow<TrackerFilterCategory> = _categoryFilter.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    fun setTimeRange(range: TimeRange) { _timeRange.value = range }
    fun setCategoryFilter(filter: TrackerFilterCategory) { _categoryFilter.value = filter }
    fun setSearchQuery(query: String) { _searchQuery.value = query }

    private val dbItems = _timeRange.flatMapLatest { range ->
        trackerConnectionDao.getFullSummary(range.sinceMs())
    }

    val items: StateFlow<List<TrackerRowUi>> = combine(
        dbItems,
        settingsRepository.observeVpnModeEnabled(),
        _categoryFilter,
        _searchQuery
    ) { list, vpnMode, filter, query ->
        list.asSequence()
            .map { it.toRowUi(vpnMode) }
            .filter { matchesFilter(it.category, filter) }
            .filter { matchesSearch(it, query) }
            .toList()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private fun TrackerFullItem.toRowUi(vpnMode: Boolean): TrackerRowUi {
        // TrafficStats tahmini kayıtlar "(trafik analizi)" içerir — DNS doğrulanmamıştır.
        val estimated = domain.contains("(trafik")
        return TrackerRowUi(
            packageName = package_name,
            appName = app_name,
            domain = domain,
            category = TrackerCategory.fromString(category),
            connectionCount = connectionCount,
            totalBytes = totalBytes,
            firstSeenMs = firstSeen,
            lastSeenMs = lastSeen,
            dnsVerified = vpnMode && !estimated
        )
    }

    private fun matchesFilter(category: TrackerCategory, filter: TrackerFilterCategory): Boolean =
        when (filter) {
            TrackerFilterCategory.ALL -> true
            TrackerFilterCategory.ADS -> category == TrackerCategory.ADVERTISING
            TrackerFilterCategory.CDN -> category == TrackerCategory.CDN
            TrackerFilterCategory.TRACKER -> category != TrackerCategory.ADVERTISING &&
                category != TrackerCategory.CDN
        }

    private fun matchesSearch(item: TrackerRowUi, query: String): Boolean {
        if (query.isBlank()) return true
        val q = query.trim().lowercase()
        return item.appName.lowercase().contains(q) || item.domain.lowercase().contains(q)
    }
}
