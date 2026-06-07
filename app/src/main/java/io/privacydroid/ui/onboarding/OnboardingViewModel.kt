package io.privacydroid.ui.onboarding

import android.os.Build
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import io.privacydroid.data.repository.OnboardingRepository
import io.privacydroid.util.PermissionHelper
import io.privacydroid.worker.WorkManagerHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

data class OnboardingUiState(
    val currentPage: Int = 0,
    val hasUsageStatsPermission: Boolean = false,
    val hasNotificationPermission: Boolean = false,
    val shouldNavigateToDashboard: Boolean = false
)

const val ONBOARDING_PAGES = 4

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val permissionHelper: PermissionHelper,
    private val onboardingRepository: OnboardingRepository,
    private val workManagerHelper: WorkManagerHelper
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        OnboardingUiState(
            hasUsageStatsPermission = permissionHelper.hasUsageStatsPermission(),
            hasNotificationPermission = isNotificationGrantedOrNotRequired()
        )
    )
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    /** Sayfa 1 (Usage Stats) için: kullanıcı ayarlardan döndüğünde çağrılır. */
    fun recheckUsageStatsPermission() {
        val granted = permissionHelper.hasUsageStatsPermission()
        _uiState.update { it.copy(hasUsageStatsPermission = granted) }
        // İzin verilmişse sayfa 2'ye otomatik geç
        if (granted && _uiState.value.currentPage == 1) {
            advancePage()
        }
    }

    /** Sayfa 2 (Bildirim) için: sistem izin diyaloğundan döndüğünde çağrılır. */
    fun onNotificationPermissionResult(granted: Boolean) {
        _uiState.update { it.copy(hasNotificationPermission = granted) }
    }

    fun advancePage() {
        val current = _uiState.value.currentPage
        if (current < ONBOARDING_PAGES - 1) {
            _uiState.update { it.copy(currentPage = current + 1) }
        }
    }

    fun goToPage(page: Int) {
        _uiState.update { it.copy(currentPage = page.coerceIn(0, ONBOARDING_PAGES - 1)) }
    }

    /**
     * Onboarding tamamlandı:
     *   1. İlk taramayı kuyruğa al — Dashboard boş durum yerine veri gösterecek.
     *   2. Tamamlandı flag'ini kaydet.
     *   3. Dashboard'a git.
     */
    fun completeOnboarding() {
        workManagerHelper.triggerImmediateScan()
        onboardingRepository.isCompleted = true
        _uiState.update { it.copy(shouldNavigateToDashboard = true) }
    }

    fun onNavigationHandled() {
        _uiState.update { it.copy(shouldNavigateToDashboard = false) }
    }

    private fun isNotificationGrantedOrNotRequired(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+: permission gerekli, başlangıçta kontrol et
            permissionHelper.hasNotificationPermission()
        } else {
            true // Android 12 ve altı: bildirim izni zorunlu değil
        }
}
