package io.privacydroid.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.privacydroid.data.local.AppDatabase
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(
        @ApplicationContext context: Context
    ): AppDatabase = Room.databaseBuilder(
        context,
        AppDatabase::class.java,
        AppDatabase.DATABASE_NAME
    )
        .addMigrations(
            AppDatabase.MIGRATION_1_2,
            AppDatabase.MIGRATION_2_3,
            AppDatabase.MIGRATION_3_4,
            AppDatabase.MIGRATION_4_5,
            AppDatabase.MIGRATION_5_6,
            AppDatabase.MIGRATION_6_7,
            AppDatabase.MIGRATION_7_8,
            AppDatabase.MIGRATION_8_9
        )
        .fallbackToDestructiveMigration()
        .build()

    @Provides
    @Singleton
    fun providePermissionLogDao(db: AppDatabase) = db.permissionLogDao()

    @Provides
    @Singleton
    fun provideCorrelationResultDao(db: AppDatabase) = db.correlationResultDao()

    @Provides
    @Singleton
    fun provideAppPermissionSnapshotDao(db: AppDatabase) = db.appPermissionSnapshotDao()

    @Provides
    @Singleton
    fun provideTrackerConnectionDao(db: AppDatabase) = db.trackerConnectionDao()

    @Provides
    @Singleton
    fun provideNotificationLogDao(db: AppDatabase) = db.notificationLogDao()

    @Provides
    @Singleton
    fun provideBlockedDomainDao(db: AppDatabase) = db.blockedDomainDao()

    @Provides
    @Singleton
    fun provideAppBlockingExceptionDao(db: AppDatabase) = db.appBlockingExceptionDao()

    @Provides
    @Singleton
    fun provideAllowedDomainDao(db: AppDatabase) = db.allowedDomainDao()
}
