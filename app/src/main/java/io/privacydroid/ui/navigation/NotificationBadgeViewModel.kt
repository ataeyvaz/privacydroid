package io.privacydroid.ui.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.privacydroid.data.local.dao.NotificationLogDao
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** Okunmamış bildirim sayısını NavGraph seviyesinde tutar — bottom nav badge için. */
@HiltViewModel
class NotificationBadgeViewModel @Inject constructor(
    notificationLogDao: NotificationLogDao
) : ViewModel() {

    val unreadCount: StateFlow<Int> = notificationLogDao.getUnreadCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
}
