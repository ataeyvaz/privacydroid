package io.privacydroid.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.privacydroid.data.local.entity.NotificationLogEntity
import kotlinx.coroutines.flow.Flow

/**
 * Uygulama içi bildirim merkezi CRUD operasyonları.
 */
@Dao
interface NotificationLogDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: NotificationLogEntity)

    /** Tüm bildirimler, en yeni önce */
    @Query("SELECT * FROM notification_logs ORDER BY detected_at DESC")
    fun getAll(): Flow<List<NotificationLogEntity>>

    /** Okunmamış bildirim sayısı — badge için */
    @Query("SELECT COUNT(*) FROM notification_logs WHERE is_read = 0")
    fun getUnreadCount(): Flow<Int>

    /** Belirli kaydı okundu işaretle */
    @Query("UPDATE notification_logs SET is_read = 1 WHERE id = :id")
    suspend fun markAsRead(id: Long)

    /** Tüm bildirimleri okundu işaretle */
    @Query("UPDATE notification_logs SET is_read = 1")
    suspend fun markAllAsRead()

    /** Eski kayıtları temizle */
    @Query("DELETE FROM notification_logs WHERE detected_at < :beforeMs")
    suspend fun deleteOlderThan(beforeMs: Long)
}
