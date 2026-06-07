package io.privacydroid.domain.usecase

import android.content.Context
import android.content.SharedPreferences
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.privacydroid.data.model.PermissionLog
import io.privacydroid.data.source.AppOpsWrapper
import io.privacydroid.data.source.MonitoredOp
import io.privacydroid.data.source.OpAccessRecord
import io.privacydroid.domain.model.SuspiciousReason
import io.privacydroid.domain.repository.PermissionRepository
import io.privacydroid.util.AppInfoHelper
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Calendar

class PermissionScanUseCaseTest {

    private lateinit var appOpsWrapper: AppOpsWrapper
    private lateinit var repository: PermissionRepository
    private lateinit var appInfoHelper: AppInfoHelper
    private lateinit var context: Context
    private lateinit var sharedPrefs: SharedPreferences
    private lateinit var prefsEditor: SharedPreferences.Editor
    private lateinit var useCase: PermissionScanUseCase

    @Before
    fun setUp() {
        appOpsWrapper = mockk()
        repository = mockk(relaxed = true)
        appInfoHelper = mockk()
        context = mockk()
        sharedPrefs = mockk()
        prefsEditor = mockk(relaxed = true)

        every { context.getSharedPreferences(any(), any()) } returns sharedPrefs
        every { sharedPrefs.getLong(any(), any()) } returns 0L
        every { sharedPrefs.edit() } returns prefsEditor
        every { prefsEditor.putLong(any(), any()) } returns prefsEditor
        every { appInfoHelper.getAppName(any()) } returns "Test Uygulaması"

        val cameraOp = MonitoredOp("android:camera", "CAMERA", "Kamera")
        val micOp = MonitoredOp("android:record_audio", "RECORD_AUDIO", "Mikrofon")
        val locationOp = MonitoredOp("android:fine_location", "ACCESS_FINE_LOCATION", "Konum")
        every { appOpsWrapper.monitoredOps } returns listOf(cameraOp, micOp, locationOp)

        useCase = PermissionScanUseCase(context, appOpsWrapper, repository, appInfoHelper)
    }

    @Test
    fun `gece saatlerinde erişim şüpheli olarak tespit edilir`() = runTest {
        val nightTimeMs = nightTimeMillis(hour = 3)
        val records = listOf(
            makeRecord("com.test.app", "CAMERA", nightTimeMs, isBackground = false)
        )
        every { appOpsWrapper.scanAllOpsAllPackages(any()) } returns records

        val result = useCase.execute()

        assertTrue(result.isSuccess)
        val scanResult = result.getOrThrow()
        val nightActivity = scanResult.suspiciousActivities.find {
            it.reason == SuspiciousReason.NIGHT_ACCESS
        }
        assertTrue("Gece erişimi tespit edilmeli", nightActivity != null)
        assertEquals("com.test.app", nightActivity!!.packageName)
    }

    @Test
    fun `gündüz saatlerinde arka plan erişimi gece şüphesi oluşturmaz`() = runTest {
        val dayTimeMs = nightTimeMillis(hour = 14) // öğleden sonra
        val records = listOf(
            makeRecord("com.test.app", "CAMERA", dayTimeMs, isBackground = false)
        )
        every { appOpsWrapper.scanAllOpsAllPackages(any()) } returns records

        val result = useCase.execute()

        assertTrue(result.isSuccess)
        val nightActivities = result.getOrThrow().suspiciousActivities.filter {
            it.reason == SuspiciousReason.NIGHT_ACCESS
        }
        assertTrue("Gündüz erişimi gece şüphesi oluşturmamalı", nightActivities.isEmpty())
    }

    @Test
    fun `arka planda kamera erişimi şüpheli olarak tespit edilir`() = runTest {
        val records = listOf(
            makeRecord("com.spy.app", "CAMERA", System.currentTimeMillis(), isBackground = true)
        )
        every { appOpsWrapper.scanAllOpsAllPackages(any()) } returns records

        val result = useCase.execute()

        assertTrue(result.isSuccess)
        val bgActivity = result.getOrThrow().suspiciousActivities.find {
            it.reason == SuspiciousReason.BACKGROUND_SENSOR_ACCESS
        }
        assertTrue("Arka plan kamera erişimi şüpheli olmalı", bgActivity != null)
    }

    @Test
    fun `1 saatte 10 konum sorgusu burst olarak tespit edilir`() = runTest {
        val now = System.currentTimeMillis()
        val locationRecords = (1..12).map { i ->
            makeRecord(
                packageName = "com.tracker.app",
                permissionType = "ACCESS_FINE_LOCATION",
                timeMs = now - (i * 60_000L), // her biri 1 dakika arayla
                isBackground = false
            )
        }
        every { appOpsWrapper.scanAllOpsAllPackages(any()) } returns locationRecords

        val result = useCase.execute()

        assertTrue(result.isSuccess)
        val burstActivity = result.getOrThrow().suspiciousActivities.find {
            it.reason == SuspiciousReason.LOCATION_BURST
        }
        assertTrue("12 konum sorgusu burst olarak tespit edilmeli", burstActivity != null)
    }

    @Test
    fun `9 konum sorgusu burst eşiğinin altında kalır`() = runTest {
        val now = System.currentTimeMillis()
        val locationRecords = (1..9).map { i ->
            makeRecord("com.maps.app", "ACCESS_FINE_LOCATION", now - (i * 60_000L), false)
        }
        every { appOpsWrapper.scanAllOpsAllPackages(any()) } returns locationRecords

        val result = useCase.execute()

        assertTrue(result.isSuccess)
        val burstActivities = result.getOrThrow().suspiciousActivities.filter {
            it.reason == SuspiciousReason.LOCATION_BURST
        }
        assertTrue("9 sorgu burst eşiğini geçmemeli", burstActivities.isEmpty())
    }

    @Test
    fun `yeni loglar repository'ye kaydedilir`() = runTest {
        val records = listOf(
            makeRecord("com.test.app", "CAMERA", System.currentTimeMillis(), false)
        )
        every { appOpsWrapper.scanAllOpsAllPackages(any()) } returns records
        coEvery { repository.saveLogs(any()) } returns Unit

        useCase.execute()

        coVerify { repository.saveLogs(match { it.size == 1 }) }
    }

    @Test
    fun `boş tarama sonucu başarıyla işlenir`() = runTest {
        every { appOpsWrapper.scanAllOpsAllPackages(any()) } returns emptyList()

        val result = useCase.execute()

        assertTrue(result.isSuccess)
        assertEquals(0, result.getOrThrow().savedLogCount)
        assertTrue(result.getOrThrow().suspiciousActivities.isEmpty())
    }

    // --- Yardımcı fonksiyonlar ---

    private fun makeRecord(
        packageName: String,
        permissionType: String,
        timeMs: Long,
        isBackground: Boolean
    ) = OpAccessRecord(
        packageName = packageName,
        opStr = "android:${permissionType.lowercase()}",
        permissionType = permissionType,
        permissionDisplayName = permissionType,
        lastAccessTimeMs = timeMs,
        durationMs = 1000L,
        isBackground = isBackground,
        accessCount = 1
    )

    /**
     * Belirli saatte bir zaman damgası üretir (bugün için).
     */
    private fun nightTimeMillis(hour: Int): Long {
        return Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, 30)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
}
