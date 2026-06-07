package io.privacydroid.data.local

import io.privacydroid.domain.model.LogFilter
import io.privacydroid.domain.model.TimeRange
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FilterQueryBuilderTest {

    @Test
    fun `temel zaman filtresi WHERE içerir`() {
        val filter = LogFilter(timeRange = TimeRange.TODAY)
        val query = FilterQueryBuilder.build(filter)
        val sql = query.sql
        assertTrue("Zaman filtresi SQL'de olmalı", sql.contains("access_time BETWEEN"))
    }

    @Test
    fun `izin türü filtresi IN içerir`() {
        val filter = LogFilter(permissionTypes = setOf("CAMERA", "RECORD_AUDIO"))
        val query = FilterQueryBuilder.build(filter)
        assertTrue("İzin türü filtresi SQL'de olmalı", query.sql.contains("permission_type IN"))
    }

    @Test
    fun `boş izin türü filtresi IN içermez`() {
        val filter = LogFilter(permissionTypes = emptySet())
        val query = FilterQueryBuilder.build(filter)
        assertFalse("Boş filtre SQL'e eklenmemeli", query.sql.contains("permission_type IN"))
    }

    @Test
    fun `arka plan filtresi is_background içerir`() {
        val filter = LogFilter(backgroundOnly = true)
        val query = FilterQueryBuilder.build(filter)
        assertTrue("Arka plan filtresi SQL'de olmalı", query.sql.contains("is_background = 1"))
    }

    @Test
    fun `arka plan filtresi false ise is_background içermez`() {
        val filter = LogFilter(backgroundOnly = false)
        val query = FilterQueryBuilder.build(filter)
        assertFalse("Arka plan filtresi SQL'e eklenmemeli", query.sql.contains("is_background"))
    }

    @Test
    fun `gece filtresi strftime içerir`() {
        val filter = LogFilter(nightOnly = true)
        val query = FilterQueryBuilder.build(filter)
        assertTrue("Gece filtresi strftime kullanmalı", query.sql.contains("strftime"))
    }

    @Test
    fun `tüm filtreler birleşince AND ile ayrılır`() {
        val filter = LogFilter(
            permissionTypes = setOf("CAMERA"),
            backgroundOnly = true,
            nightOnly = true
        )
        val query = FilterQueryBuilder.build(filter)
        val andCount = query.sql.split(" AND ").size - 1
        assertTrue("En az 3 AND koşulu olmalı", andCount >= 3)
    }

    @Test
    fun `ORDER BY her zaman eklenir`() {
        val filter = LogFilter()
        val query = FilterQueryBuilder.build(filter)
        assertTrue("ORDER BY access_time DESC olmalı", query.sql.contains("ORDER BY access_time DESC"))
    }
}
