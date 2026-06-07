package io.privacydroid.worker

import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.WorkManager
import io.mockk.mockk
import io.mockk.verify
import io.privacydroid.data.model.MonitoringMode
import org.junit.Before
import org.junit.Test

class WorkManagerHelperTest {

    private lateinit var workManager: WorkManager
    private lateinit var helper: WorkManagerHelper

    @Before
    fun setUp() {
        workManager = mockk(relaxed = true)
        helper = WorkManagerHelper(workManager)
    }

    @Test
    fun `schedulePeriodic enqueueUniquePeriodicWork çağırır`() {
        helper.schedulePeriodic()
        verify {
            workManager.enqueueUniquePeriodicWork(
                PermissionScanWorker.WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                any()
            )
        }
    }

    @Test
    fun `cancelPeriodic cancelUniqueWork çağırır`() {
        helper.cancelPeriodic()
        verify { workManager.cancelUniqueWork(PermissionScanWorker.WORK_NAME) }
    }

    @Test
    fun `applyMonitoringMode PERIODIC planlamayı başlatır`() {
        helper.applyMonitoringMode(MonitoringMode.PERIODIC)
        verify { workManager.enqueueUniquePeriodicWork(any(), any(), any()) }
    }

    @Test
    fun `applyMonitoringMode REALTIME periyodik görevi iptal eder`() {
        helper.applyMonitoringMode(MonitoringMode.REALTIME)
        verify { workManager.cancelUniqueWork(PermissionScanWorker.WORK_NAME) }
    }
}
