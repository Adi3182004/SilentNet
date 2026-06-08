package com.silentnet.lostlink.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.silentnet.lostlink.data.*

@Database(
    entities = [
        LostLinkSightingEntity::class,
        LostLinkBeaconEntity::class,
        LostLinkRecoveryEntity::class,
        LostLinkAssetEntity::class,
        LostLinkRelayEntity::class,
        LostLinkEmergencyEntity::class,
        LostLinkAnalyticsEntity::class,
        LostLinkObservationEntity::class,
        UserProfileEntity::class,
        AssetEntity::class,
        RecoveryObservationEntity::class,
        TrustedDeviceEntity::class,
        LostCaseEntity::class,
        RecoverySightingEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class LostLinkDatabase : RoomDatabase() {
    abstract fun sightingDao(): LostLinkSightingDao
    abstract fun beaconDao(): LostLinkBeaconDao
    abstract fun recoveryDao(): LostLinkRecoveryDao
    abstract fun assetDao(): LostLinkAssetDao
    abstract fun relayDao(): LostLinkRelayDao
    abstract fun emergencyDao(): LostLinkEmergencyDao
    abstract fun analyticsDao(): LostLinkAnalyticsDao
    abstract fun observationDao(): LostLinkObservationDao
    abstract fun userProfileDao(): UserProfileDao
    abstract fun recoveryAssetDao(): AssetDao
    abstract fun recoveryObservationDao(): RecoveryObservationDao
    abstract fun trustedDeviceDao(): TrustedDeviceDao
    abstract fun lostCaseDao(): LostCaseDao
    abstract fun recoverySightingDao(): RecoverySightingDao

    companion object {
        @Volatile
        private var INSTANCE: LostLinkDatabase? = null

        fun getDatabase(context: Context): LostLinkDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    LostLinkDatabase::class.java,
                    "lostlink_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
