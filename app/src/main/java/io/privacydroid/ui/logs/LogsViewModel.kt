package io.privacydroid.ui.logs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.privacydroid.data.local.dao.TrackerConnectionDao
import io.privacydroid.data.local.entity.TrackerConnectionEntity
import io.privacydroid.data.model.PermissionLog
import io.privacydroid.data.model.PermissionType
import io.privacydroid.domain.model.LogFilter
import io.privacydroid.domain.repository.PermissionRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import javax.inject.Inject

data class LogsUiState(
    val isLoading: Boolean = true,
    val logs: List<PermissionLog> = emptyList(),
    val trackerLogs: List<TrackerConnectionEntity> = emptyList(),
    val filter: LogFilter = LogFilter(),
    val selectedLog: PermissionLog? = null,
    val isEmpty: Boolean = false
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class LogsViewModel @Inject constructor(
    private val repository: PermissionRepository,
    private val trackerConnectionDao: TrackerConnectionDao
) : ViewModel() {

    private val _filter = MutableStateFlow(LogFilter())

    private val since7Days get() = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(7)

    val uiState: StateFlow<LogsUiState> = combine(
        _filter.flatMapLatest { filter ->
            repository.getFilteredLogs(filter)
                .map { logs -> Pair(filter, logs) }
                .catch { emit(Pair(filter, emptyList())) }
        },
        trackerConnectionDao.getRecentRaw(sinceMs = since7Days)
    ) { (filter, logs), trackerLogs ->
        val showTracker = filter.showTrackerOnly
        LogsUiState(
            isLoading = false,
            logs = if (showTracker) emptyList() else logs,
            trackerLogs = if (showTracker) trackerLogs else emptyList(),
            filter = filter,
            selectedLog = null,
            isEmpty = if (showTracker) trackerLogs.isEmpty() else logs.isEmpty()
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LogsUiState())

    fun updateFilter(filter: LogFilter) {
        _filter.update { filter }
    }

    fun selectLog(log: PermissionLog) {
        // uiState.selectedLog — Dialog state; filter flow'u tetiklemeden güncellemek için
        // Basit StateFlow ile halledelim
        _selectedLog.value = log
    }

    fun dismissDetail() {
        _selectedLog.value = null
    }

    private val _selectedLog = MutableStateFlow<PermissionLog?>(null)
    val selectedLog: StateFlow<PermissionLog?> = _selectedLog

    /**
     * Seçilen log için sade, anlaşılır Türkçe açıklama üretir.
     * Teknik terim kullanmaz.
     */
    fun buildFriendlyMessage(log: PermissionLog): String {
        val dateFmt = SimpleDateFormat("d MMMM 'saat' HH:mm", Locale("tr")).apply { timeZone = TimeZone.getDefault() }
        val dateStr = dateFmt.format(Date(log.accessTime))

        val permName = when (log.permissionType) {
            PermissionType.CAMERA            -> "kameranıza"
            PermissionType.MICROPHONE        -> "mikrofonunuza"
            PermissionType.LOCATION_FINE,
            PermissionType.LOCATION_COARSE   -> "konumunuza"
            PermissionType.CONTACTS          -> "rehberinize"
            PermissionType.CALL_LOG          -> "arama geçmişinize"
            PermissionType.SMS               -> "SMS'lerinize"
            PermissionType.CALENDAR          -> "takviminize"
            PermissionType.PHONE             -> "telefon bilgilerinize"
            else                             -> "verilerinize"
        }

        val cal = Calendar.getInstance().apply { timeInMillis = log.accessTime }
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        val isSleepTime = hour in 0..6

        val contextParts = mutableListOf<String>()
        if (isSleepTime) contextParts.add("uyku saatlerinizde")
        val contextPrefix = if (contextParts.isNotEmpty()) contextParts.joinToString(", ") + " " else ""

        val bgNote = if (log.isBackground)
            " ${log.appName} o sırada arka plandaydı; yani siz kullanmıyordunuz."
        else ""

        val abnormalNote = when {
            log.isBackground && isSleepTime ->
                " Bu, uyku saatlerinde arka planda gerçekleşen erişimdir ve anormal kabul edilir."
            log.isBackground ->
                " Arka planda erişim şüpheli olabilir."
            isSleepTime ->
                " Uyku saatlerinde gerçekleşen erişim dikkat gerektirebilir."
            else -> ""
        }

        return "${log.appName}, $dateStr tarihinde ${contextPrefix}$permName erişti.$bgNote$abnormalNote"
    }
}
