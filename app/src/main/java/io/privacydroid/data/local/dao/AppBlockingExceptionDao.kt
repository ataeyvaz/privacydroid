package io.privacydroid.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.privacydroid.data.local.entity.AppBlockingExceptionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AppBlockingExceptionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: AppBlockingExceptionEntity)

    @Query("DELETE FROM app_blocking_exceptions WHERE package_name = :packageName")
    suspend fun delete(packageName: String)

    /** Tüm istisna kayıtları (UI listesi için). */
    @Query("SELECT * FROM app_blocking_exceptions ORDER BY added_at DESC")
    fun observeAll(): Flow<List<AppBlockingExceptionEntity>>

    /** Yalnızca paket adları — VPN servisi hızlı kontrol için bellekte tutar. */
    @Query("SELECT package_name FROM app_blocking_exceptions")
    fun observePackageNames(): Flow<List<String>>

    /** Belirli bir uygulama istisna mı? — Uygulama Detay toggle'ı için. */
    @Query("SELECT EXISTS(SELECT 1 FROM app_blocking_exceptions WHERE package_name = :packageName)")
    fun observeIsException(packageName: String): Flow<Boolean>
}
