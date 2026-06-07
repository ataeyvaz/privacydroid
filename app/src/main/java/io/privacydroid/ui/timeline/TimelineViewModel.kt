package io.privacydroid.ui.timeline

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.privacydroid.domain.model.LogFilter
import io.privacydroid.domain.repository.PermissionRepository
import io.privacydroid.ui.dashboard.permissionColor
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import javax.inject.Inject

data class TimelineUiState(
    val isLoading: Boolean = true,
    val rows: List<TimelineRow> = emptyList(),
    val filter: LogFilter = LogFilter(),
    val isEmpty: Boolean = false,
    val isFilteredEmpty: Boolean = false
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TimelineViewModel @Inject constructor(
    private val repository: PermissionRepository
) : ViewModel() {

    private val _filter = MutableStateFlow(LogFilter())

    val uiState: StateFlow<TimelineUiState> = _filter
        .flatMapLatest { filter ->
            repository.getFilteredLogs(filter)
                .map { logs ->
                    val rows = logs
                        .groupBy { it.packageName }
                        .map { (pkg, pkgLogs) ->
                            TimelineRow(
                                appName = pkgLogs.first().appName,
                                packageName = pkg,
                                entries = pkgLogs.map { log ->
                                    TimelineEntry(
                                        accessTimeMs = log.accessTime,
                                        permissionColor = permissionColor(log.permissionType.name),
                                        isBackground = log.isBackground,
                                        permissionLabel = log.permissionType.displayName
                                    )
                                }.sortedBy { it.accessTimeMs }
                            )
                        }
                        .sortedByDescending { it.entries.size }

                    TimelineUiState(
                        isLoading = false,
                        rows = rows,
                        filter = filter,
                        isEmpty = logs.isEmpty() && filter == LogFilter(),
                        isFilteredEmpty = logs.isEmpty() && filter != LogFilter()
                    )
                }
                .catch { emit(TimelineUiState(isLoading = false)) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TimelineUiState())

    fun updateFilter(filter: LogFilter) {
        _filter.update { filter }
    }
}
