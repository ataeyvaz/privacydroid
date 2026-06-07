package io.privacydroid.ui.onboarding

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.privacydroid.data.repository.OnboardingRepository
import io.privacydroid.util.PermissionHelper
import io.privacydroid.worker.WorkManagerHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {

    private lateinit var permissionHelper: PermissionHelper
    private lateinit var onboardingRepository: OnboardingRepository
    private lateinit var workManagerHelper: WorkManagerHelper
    private lateinit var viewModel: OnboardingViewModel

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        permissionHelper = mockk()
        onboardingRepository = mockk(relaxed = true)
        workManagerHelper = mockk(relaxed = true)

        every { permissionHelper.hasUsageStatsPermission() } returns false
        every { permissionHelper.hasNotificationPermission() } returns false
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `başlangıçta izin yoksa hasUsageStatsPermission false`() {
        every { permissionHelper.hasUsageStatsPermission() } returns false
        viewModel = createViewModel()
        assertFalse(viewModel.uiState.value.hasUsageStatsPermission)
    }

    @Test
    fun `izin verildikten sonra recheck izni güncelleştirir`() {
        every { permissionHelper.hasUsageStatsPermission() } returns false
        viewModel = createViewModel()

        every { permissionHelper.hasUsageStatsPermission() } returns true
        viewModel.recheckUsageStatsPermission()

        assertTrue(viewModel.uiState.value.hasUsageStatsPermission)
    }

    @Test
    fun `sayfa 1'deyken izin verilince otomatik sayfa 2'ye geçilir`() {
        every { permissionHelper.hasUsageStatsPermission() } returns false
        viewModel = createViewModel()
        viewModel.advancePage() // sayfa 1'e git

        every { permissionHelper.hasUsageStatsPermission() } returns true
        viewModel.recheckUsageStatsPermission()

        assertEquals(2, viewModel.uiState.value.currentPage)
    }

    @Test
    fun `advancePage son sayfada çalışmaz`() {
        every { permissionHelper.hasUsageStatsPermission() } returns true
        viewModel = createViewModel()

        repeat(ONBOARDING_PAGES + 5) { viewModel.advancePage() }

        assertEquals(ONBOARDING_PAGES - 1, viewModel.uiState.value.currentPage)
    }

    @Test
    fun `completeOnboarding tarama başlatır ve completed kaydeder`() {
        every { permissionHelper.hasUsageStatsPermission() } returns true
        viewModel = createViewModel()

        viewModel.completeOnboarding()

        verify { workManagerHelper.triggerImmediateScan() }
        verify { onboardingRepository.isCompleted = true }
        assertTrue(viewModel.uiState.value.shouldNavigateToDashboard)
    }

    @Test
    fun `onNavigationHandled shouldNavigateToDashboard'u sıfırlar`() {
        every { permissionHelper.hasUsageStatsPermission() } returns true
        viewModel = createViewModel()
        viewModel.completeOnboarding()

        viewModel.onNavigationHandled()

        assertFalse(viewModel.uiState.value.shouldNavigateToDashboard)
    }

    @Test
    fun `bildirim izni sonucu state'e yansır`() {
        viewModel = createViewModel()
        viewModel.onNotificationPermissionResult(true)
        assertTrue(viewModel.uiState.value.hasNotificationPermission)

        viewModel.onNotificationPermissionResult(false)
        assertFalse(viewModel.uiState.value.hasNotificationPermission)
    }

    private fun createViewModel() = OnboardingViewModel(
        permissionHelper = permissionHelper,
        onboardingRepository = onboardingRepository,
        workManagerHelper = workManagerHelper
    )
}
