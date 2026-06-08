package com.silentnet.lostlink.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface LostLinkSightingDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(sighting: LostLinkSightingEntity)

    @Query("SELECT * FROM lostlink_sightings WHERE deviceId = :deviceId ORDER BY timestamp DESC")
    fun getSightingsForDevice(deviceId: String): Flow<List<LostLinkSightingEntity>>

    @Query("SELECT * FROM lostlink_sightings ORDER BY timestamp DESC LIMIT 100")
    fun getAllSightings(): Flow<List<LostLinkSightingEntity>>
}

@Dao
interface LostLinkBeaconDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(beacon: LostLinkBeaconEntity)

    @Query("SELECT * FROM lostlink_beacons WHERE ownerId = :ownerId")
    fun getBeaconsByOwner(ownerId: String): Flow<List<LostLinkBeaconEntity>>
}

@Dao
interface LostLinkRecoveryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(recovery: LostLinkRecoveryEntity)

    @Query("SELECT * FROM lostlink_recovery WHERE deviceId = :deviceId")
    suspend fun getRecoveryState(deviceId: String): LostLinkRecoveryEntity?

    @Query("UPDATE lostlink_recovery SET isLost = :isLost, updatedAt = :timestamp WHERE deviceId = :deviceId")
    suspend fun updateLostStatus(deviceId: String, isLost: Boolean, timestamp: Long)
}

@Dao
interface LostLinkAssetDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(asset: LostLinkAssetEntity)

    @Query("SELECT * FROM lostlink_assets WHERE ownerId = :ownerId")
    fun getAssetsByOwner(ownerId: String): Flow<List<LostLinkAssetEntity>>
}

@Dao
interface LostLinkRelayDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(relay: LostLinkRelayEntity)

    @Query("SELECT * FROM lostlink_relay WHERE nodeId = :nodeId")
    suspend fun getRelayStats(nodeId: String): LostLinkRelayEntity?

    @Query("SELECT * FROM lostlink_relay ORDER BY relayContributionScore DESC")
    fun getAllRelayStats(): Flow<List<LostLinkRelayEntity>>
}

@Dao
interface LostLinkEmergencyDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(emergency: LostLinkEmergencyEntity)

    @Query("SELECT * FROM lostlink_emergency WHERE expirationTime > :now ORDER BY priority DESC")
    fun getActiveEmergencies(now: Long): Flow<List<LostLinkEmergencyEntity>>
}

@Dao
interface LostLinkAnalyticsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(analytics: LostLinkAnalyticsEntity)

    @Query("SELECT * FROM lostlink_analytics WHERE id = 1")
    fun getAnalytics(): Flow<LostLinkAnalyticsEntity?>
}

@Dao
interface LostLinkObservationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(observation: LostLinkObservationEntity)

    @Update
    suspend fun update(observation: LostLinkObservationEntity)

    @Query("SELECT * FROM lostlink_observations WHERE beaconId = :beaconId AND observerId = :observerId LIMIT 1")
    suspend fun getObservation(beaconId: String, observerId: String): LostLinkObservationEntity?

    @Query("SELECT * FROM lostlink_observations ORDER BY lastSeen DESC")
    fun getAllObservations(): Flow<List<LostLinkObservationEntity>>

    @Query("SELECT * FROM lostlink_observations WHERE beaconId = :beaconId ORDER BY confidence DESC")
    suspend fun getObservationsByBeacon(beaconId: String): List<LostLinkObservationEntity>
}
