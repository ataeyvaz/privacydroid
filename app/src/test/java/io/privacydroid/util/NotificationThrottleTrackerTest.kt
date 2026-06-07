package io.privacydroid.util

import android.content.Context
import android.content.SharedPreferences
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class NotificationThrottleTrackerTest {

    private lateinit var context: Context
    private lateinit var prefs: SharedPreferences
    private lateinit var editor: SharedPreferences.Editor
    private lateinit var tracker: NotificationThrottleTracker

    @Before
    fun setUp() {
        context = mockk()
        prefs = mockk()
        editor = mockk(relaxed = true)
        every { context.getSharedPreferences(any(), any()) } returns prefs
        every { prefs.edit() } returns editor
        every { editor.putLong(any(), any()) } returns editor
        tracker = NotificationThrottleTracker(context)
    }

    @Test
    fun `kayıt yoksa bildirim gönderilmeli`() {
        every { prefs.getLong(any(), 0L) } returns 0L
        assertTrue(tracker.shouldNotify("com.test.app", "NIGHT_ACCESS"))
    }

    @Test
    fun `cooldown içinde tekrar bildirim gönderilmemeli`() {
        val recent = System.currentTimeMillis() - 1_000L // 1 saniye önce
        every { prefs.getLong(any(), 0L) } returns recent
        assertFalse(tracker.shouldNotify("com.test.app", "NIGHT_ACCESS"))
    }

    @Test
    fun `cooldown geçtikten sonra bildirim gönderilmeli`() {
        val old = System.currentTimeMillis() - NotificationThrottleTracker.COOLDOWN_MS - 1_000L
        every { prefs.getLong(any(), 0L) } returns old
        assertTrue(tracker.shouldNotify("com.test.app", "NIGHT_ACCESS"))
    }

    @Test
    fun `farklı reason aynı app için bağımsız takip edilir`() {
        val recent = System.currentTimeMillis() - 1_000L
        every { prefs.getLong(match { it.contains("NIGHT_ACCESS") }, 0L) } returns recent
        every { prefs.getLong(match { it.contains("BACKGROUND_SENSOR_ACCESS") }, 0L) } returns 0L

        assertFalse(tracker.shouldNotify("com.test.app", "NIGHT_ACCESS"))
        assertTrue(tracker.shouldNotify("com.test.app", "BACKGROUND_SENSOR_ACCESS"))
    }

    @Test
    fun `markNotified SharedPreferences'a yazar`() {
        tracker.markNotified("com.test.app", "NIGHT_ACCESS")
        verify { editor.putLong(any(), any()) }
        verify { editor.apply() }
    }

    @Test
    fun `resetAll tüm kayıtları temizler`() {
        tracker.resetAll()
        verify { editor.clear() }
        verify { editor.apply() }
    }
}
