package io.privacydroid.data.local.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.privacydroid.data.local.AppDatabase
import io.privacydroid.data.local.entity.PermissionLogEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

/**
 * Room DAO integration testleri.
 * In-memory database kullanır — gerçek veriyi etkilemez.
 */
@RunWith(AndroidJUnit4::class)
class PermissionLogDaoTest {

    private lateinit var database: AppDatabase
    private lateinit var dao: PermissionLogDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.permissionLogDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `insert ve getRecentLogs çalışır`() = runTest {
        val log = makeLog(packageName = "com.test", permissionType = "CAMERA")
        dao.insert(log)

        val results = dao.getRecentLogs(10).first()
        assertEquals(1, results.size)
        assertEquals("com.test", results.first().packageName)
    }

    @Test
    fun `getLogsForApp yalnızca belirtilen paketi döner`() = runTest {
        dao.insertAll(
            listOf(
                makeLog("com.app.one", "CAMERA"),
                makeLog("com.app.two", "RECORD_AUDIO"),
                makeLog("com.app.one", "RECORD_AUDIO")
            )
        )

        val results = dao.getLogsForApp("com.app.one").first()
        assertEquals(2, results.size)
        assertTrue(results.all { it.packageName == "com.app.one" })
    }

    @Test
    fun `getBackgroundAccessesAfter yalnızca arka plan erişimleri döner`() = runTest {
        val now = System.currentTimeMillis()
        dao.insertAll(
            listOf(
                makeLog("com.fg.app", "CAMERA", isBackground = false, accessTime = now),
                makeLog("com.bg.app", "CAMERA", isBackground = true, accessTime = now)
            )
        )

        val results = dao.getBackgroundAccessesAfter(now - 1000).first()
        assertEquals(1, results.size)
        assertTrue(results.first().isBackground)
    }

    @Test
    fun `deleteOlderThan eski logları siler`() = runTest {
        val old = System.currentTimeMillis() - 10_000L
        val now = System.currentTimeMillis()

        dao.insertAll(
            listOf(
                makeLog("com.old", "CAMERA", createdAt = old),
                makeLog("com.new", "CAMERA", createdAt = now)
            )
        )

        val deleted = dao.deleteOlderThan(now - 5_000L)
        assertEquals(1, deleted)

        val remaining = dao.getRecentLogs(10).first()
        assertEquals(1, remaining.size)
        assertEquals("com.new", remaining.first().packageName)
    }

    @Test
    fun `existsLog var olan kaydı tespit eder`() = runTest {
        val time = System.currentTimeMillis()
        dao.insert(makeLog("com.test", "CAMERA", accessTime = time))

        val count = dao.existsLog("com.test", "CAMERA", time)
        assertEquals(1, count)
    }

    private fun makeLog(
        packageName: String,
        permissionType: String,
        isBackground: Boolean = false,
        accessTime: Long = System.currentTimeMillis(),
        createdAt: Long = System.currentTimeMillis()
    ) = PermissionLogEntity(
        packageName = packageName,
        appName = "Test App",
        permissionType = permissionType,
        accessTime = accessTime,
        durationMs = 500L,
        isBackground = isBackground,
        createdAt = createdAt
    )
}
