package io.privacydroid.domain.usecase

import io.privacydroid.data.model.PermissionLog
import io.privacydroid.data.model.PermissionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Calendar

class RiskScoreCalculatorTest {

    private lateinit var calculator: RiskScoreCalculator

    @Before
    fun setUp() {
        calculator = RiskScoreCalculator()
    }

    @Test
    fun `boş log listesi sıfır risk skoru verir`() {
        val result = calculator.calculate(emptyList(), "com.test")
        assertEquals(0, result.riskScore)
    }

    @Test
    fun `tüm erişimler arka planda ve geceyse skor yüksek olmalı`() {
        val logs = (1..10).map { makeLog(isBackground = true, hour = 3) }
        val result = calculator.calculate(logs, "com.test")
        assertTrue("Skor 50'nin üzerinde olmalı", result.riskScore > 50)
    }

    @Test
    fun `hiç arka plan erişimi yoksa background skoru sıfır`() {
        val logs = (1..5).map { makeLog(isBackground = false, hour = 14) }
        val result = calculator.calculate(logs, "com.test")
        assertEquals(0f, result.riskComponents.backgroundScore, 0.01f)
    }

    @Test
    fun `tüm erişimler arka plandaysa background skoru 40`() {
        val logs = (1..5).map { makeLog(isBackground = true, hour = 14) }
        val result = calculator.calculate(logs, "com.test")
        assertEquals(40f, result.riskComponents.backgroundScore, 0.01f)
    }

    @Test
    fun `5 veya daha fazla gece erişimi maksimum gece skoru verir`() {
        val logs = (1..5).map { makeLog(isBackground = false, hour = 2) }
        val result = calculator.calculate(logs, "com.test")
        assertEquals(30f, result.riskComponents.nightScore, 0.01f)
    }

    @Test
    fun `gece erişimi yoksa gece skoru sıfır`() {
        val logs = (1..5).map { makeLog(isBackground = false, hour = 10) }
        val result = calculator.calculate(logs, "com.test")
        assertEquals(0f, result.riskComponents.nightScore, 0.01f)
    }

    @Test
    fun `5 farklı izin türü maksimum çeşitlilik skoru verir`() {
        val logs = listOf(
            makeLog(PermissionType.CAMERA),
            makeLog(PermissionType.MICROPHONE),
            makeLog(PermissionType.LOCATION_FINE),
            makeLog(PermissionType.CONTACTS),
            makeLog(PermissionType.CALL_LOG)
        )
        val result = calculator.calculate(logs, "com.test")
        assertEquals(20f, result.riskComponents.diversityScore, 0.01f)
    }

    @Test
    fun `skor 0-100 aralığında kalır`() {
        // Kötü senaryo: hepsi arka plan + gece + çok sayıda
        val logs = (1..100).map { makeLog(isBackground = true, hour = 4) }
        val result = calculator.calculate(logs, "com.test")
        assertTrue("Skor en fazla 100 olmalı", result.riskScore <= 100)
        assertTrue("Skor en az 0 olmalı", result.riskScore >= 0)
    }

    @Test
    fun `haftalık veri 7 gün içerir`() {
        val logs = listOf(makeLog(isBackground = false, hour = 10))
        val result = calculator.calculate(logs, "com.test")
        assertEquals(7, result.weeklyData.size)
    }

    // ---- Yardımcılar ----

    private fun makeLog(
        permType: PermissionType = PermissionType.CAMERA,
        isBackground: Boolean = false,
        hour: Int = 12
    ) = PermissionLog(
        packageName = "com.test",
        appName = "Test App",
        permissionType = permType,
        accessTime = todayAt(hour),
        isBackground = isBackground
    )

    private fun todayAt(hour: Int): Long =
        Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
}
