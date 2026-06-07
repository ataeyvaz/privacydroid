package io.privacydroid.ui.blocking

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.map
import dagger.hilt.android.lifecycle.HiltViewModel
import io.privacydroid.data.local.dao.AllowedDomainDao
import io.privacydroid.data.local.dao.AppBlockingExceptionDao
import io.privacydroid.data.local.dao.BlockedDomainDao
import io.privacydroid.data.local.entity.AllowedDomainEntity
import io.privacydroid.data.local.entity.AppBlockingExceptionEntity
import io.privacydroid.data.local.entity.BlockedDomainEntity
import io.privacydroid.data.model.BlockingMode
import io.privacydroid.data.repository.SettingsRepository
import io.privacydroid.ui.common.TimeRange
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

data class BlockingSummaryUi(
    val adsBlocked: Int = 0,
    val trackersBlocked: Int = 0,
    val allowed: Int = 0
)

data class TopBlockedDomain(
    val domain: String,
    val count: Int
)

data class BlockedRowUi(
    val id: Long,
    val domain: String,
    val appName: String,
    val packageName: String,
    val blockType: String,        // AD / TRACKER
    val blockedAtMs: Long,
    val timeLabel: String
) {
    /** Gerçek bir paket adı mı? (uid:/unknown değil) — "İzin Ver" butonu için. */
    val isRealPackage: Boolean
        get() = packageName.contains(".") &&
            !packageName.startsWith("uid:") && packageName != "unknown"
}

data class AllowedRowUi(
    val id: Long,
    val domain: String,
    val appName: String,
    val packageName: String,
    val allowReason: String,      // WHITELIST / USER_EXCEPTION / NORMAL
    val timeLabel: String
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class BlockingDetailsViewModel @Inject constructor(
    private val blockedDomainDao: BlockedDomainDao,
    private val allowedDomainDao: AllowedDomainDao,
    private val appBlockingExceptionDao: AppBlockingExceptionDao,
    settingsRepository: SettingsRepository
) : ViewModel() {

    private val _timeRange = MutableStateFlow(TimeRange.TODAY)
    val timeRange: StateFlow<TimeRange> = _timeRange.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    fun setTimeRange(range: TimeRange) { _timeRange.value = range }
    fun setSearchQuery(query: String) { _searchQuery.value = query }

    /** Mevcut engelleme modu — detay popup'ında "Engelleme modu" satırı için. */
    val blockingMode: StateFlow<BlockingMode> = settingsRepository.observeBlockingMode()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BlockingMode.OFF)

    /** Üst özet: reklam / tracker / izin verilen sayıları (zaman filtresine göre). */
    val summary: StateFlow<BlockingSummaryUi> = _timeRange.flatMapLatest { range ->
        val since = range.sinceMs()
        combine(
            blockedDomainDao.countByTypeSince("AD", since),
            blockedDomainDao.countByTypeSince("TRACKER", since),
            allowedDomainDao.countSince(since)
        ) { ads, trackers, allowed -> BlockingSummaryUi(ads, trackers, allowed) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BlockingSummaryUi())

    /** En çok engellenen domainler (ilk 10). */
    val topBlocked: StateFlow<List<TopBlockedDomain>> = _timeRange.flatMapLatest { range ->
        blockedDomainDao.blockedSummary(range.sinceMs(), limit = 10)
            .map { list -> list.map { TopBlockedDomain(it.domain, it.count) } }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val rangeAndQuery = combine(_timeRange, _searchQuery) { r, q -> r to q }

    /** Engellenenler — sonsuz kaydırma (Paging 3). */
    val blockedPaged: Flow<PagingData<BlockedRowUi>> = rangeAndQuery.flatMapLatest { (range, query) ->
        Pager(PagingConfig(pageSize = 30, enablePlaceholders = false)) {
            blockedDomainDao.pagedBlocked(range.sinceMs(), query.trim())
        }.flow.map { data -> data.map { it.toRowUi() } }
    }.cachedIn(viewModelScope)

    /** İzin verilenler — sonsuz kaydırma (Paging 3). */
    val allowedPaged: Flow<PagingData<AllowedRowUi>> = rangeAndQuery.flatMapLatest { (range, query) ->
        Pager(PagingConfig(pageSize = 30, enablePlaceholders = false)) {
            allowedDomainDao.pagedAllowed(range.sinceMs(), query.trim())
        }.flow.map { data -> data.map { it.toRowUi() } }
    }.cachedIn(viewModelScope)

    /** "İzin Ver" — uygulamayı engellemeden hariç tut; bir daha engellenmez. */
    fun allowApp(packageName: String, appName: String) {
        viewModelScope.launch {
            appBlockingExceptionDao.insert(
                AppBlockingExceptionEntity(packageName = packageName, appName = appName)
            )
        }
    }

    private fun BlockedDomainEntity.toRowUi() = BlockedRowUi(
        id = id,
        domain = domain,
        appName = appName,
        packageName = packageName,
        blockType = blockType,
        blockedAtMs = blockedAt,
        timeLabel = formatTime(blockedAt)
    )

    private fun AllowedDomainEntity.toRowUi() = AllowedRowUi(
        id = id,
        domain = domain,
        appName = appName,
        packageName = packageName,
        allowReason = allowReason,
        timeLabel = formatTime(allowedAt)
    )

    private fun formatTime(timeMs: Long): String =
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timeMs))
}
