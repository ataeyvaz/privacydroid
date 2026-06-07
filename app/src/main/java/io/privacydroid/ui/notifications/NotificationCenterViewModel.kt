package io.privacydroid.ui.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.privacydroid.data.local.dao.NotificationLogDao
import io.privacydroid.data.local.entity.NotificationLogEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject

enum class NotificationFilter { ALL, CRITICAL, MEDIUM, READ }

data class NotificationGroup(
    val dateLabel: String,
    val items: List<NotificationLogEntity>
)

@HiltViewModel
class NotificationCenterViewModel @Inject constructor(
    private val dao: NotificationLogDao
) : ViewModel() {

    val filter = MutableStateFlow(NotificationFilter.ALL)

    val unreadCount: StateFlow<Int> = dao.getUnreadCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val groupedNotifications: StateFlow<List<NotificationGroup>> = combine(
        dao.getAll(),
        filter
    ) { logs, f ->
        Timber.d("NotificationCenter: Room'dan ${logs.size} kayıt geldi (filtre=$f)")
        val filtered = when (f) {
            NotificationFilter.ALL -> logs
            NotificationFilter.CRITICAL -> logs.filter { it.riskLevel == "CRITICAL" }
            NotificationFilter.MEDIUM -> logs.filter { it.riskLevel != "CRITICAL" && !it.isRead }
            NotificationFilter.READ -> logs.filter { it.isRead }
        }
        groupByDate(filtered)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setFilter(f: NotificationFilter) { filter.value = f }

    fun markAsRead(id: Long) = viewModelScope.launch { dao.markAsRead(id) }

    fun markAllAsRead() = viewModelScope.launch { dao.markAllAsRead() }

    private fun groupByDate(logs: List<NotificationLogEntity>): List<NotificationGroup> {
        if (logs.isEmpty()) return emptyList()

        val todayStart = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val yesterdayStart = todayStart - TimeUnit.DAYS.toMillis(1)
        val dateFmt = SimpleDateFormat("dd MMMM", Locale("tr"))

        val result = mutableListOf<NotificationGroup>()
        var currentLabel = ""
        var currentItems = mutableListOf<NotificationLogEntity>()

        for (log in logs) {
            val label = when {
                log.detectedAt >= todayStart -> "Bugün"
                log.detectedAt >= yesterdayStart -> "Dün"
                else -> dateFmt.format(Date(log.detectedAt))
            }
            if (label != currentLabel) {
                if (currentItems.isNotEmpty()) {
                    result.add(NotificationGroup(currentLabel, currentItems.toList()))
                }
                currentLabel = label
                currentItems = mutableListOf()
            }
            currentItems.add(log)
        }
        if (currentItems.isNotEmpty()) result.add(NotificationGroup(currentLabel, currentItems.toList()))

        return result
    }
}
