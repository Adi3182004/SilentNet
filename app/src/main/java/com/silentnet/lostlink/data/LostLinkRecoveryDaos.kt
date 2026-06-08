package com.silentnet.lostlink.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TrustedDeviceDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(device: TrustedDeviceEntity)

    @Query("SELECT * FROM trusted_devices WHERE deviceId = :deviceId")
    suspend fun getDevice(deviceId: String): TrustedDeviceEntity?

    @Query("SELECT * FROM trusted_devices")
    fun getAllTrustedDevices(): Flow<List<TrustedDeviceEntity>>

    @Delete
    suspend fun delete(device: TrustedDeviceEntity)
}

@Dao
interface AssetDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(asset: AssetEntity)

    @Query("SELECT * FROM recovery_assets WHERE assetId = :assetId")
    suspend fun getAsset(assetId: String): AssetEntity?

    @Query("SELECT * FROM recovery_assets WHERE assetId = :assetId")
    fun getAssetSync(assetId: String): AssetEntity?

    @Query("SELECT * FROM recovery_assets WHERE ownerId = :ownerId")
    fun getAssetsByOwner(ownerId: String): Flow<List<AssetEntity>>

    @Query("UPDATE recovery_assets SET isLost = :isLost WHERE assetId = :assetId")
    suspend fun updateLostStatus(assetId: String, isLost: Boolean)

    @Query("SELECT * FROM recovery_assets WHERE isLost = 1")
    fun getLostAssets(): Flow<List<AssetEntity>>

    @Query("SELECT * FROM recovery_assets WHERE linkedDeviceId = :deviceId")
    suspend fun getAssetByLinkedDevice(deviceId: String): AssetEntity?

    @Query("SELECT * FROM recovery_assets WHERE assetName LIKE '%' || :query || '%' OR assetId LIKE '%' || :query || '%'")
    fun searchAssets(query: String): Flow<List<AssetEntity>>
}

@Dao
interface LostCaseDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(lostCase: LostCaseEntity)

    @Query("SELECT * FROM lost_cases WHERE assetId = :assetId AND status = 'ACTIVE'")
    suspend fun getActiveCaseForAsset(assetId: String): LostCaseEntity?

    @Query("SELECT * FROM lost_cases WHERE status = 'ACTIVE'")
    fun getAllActiveCases(): Flow<List<LostCaseEntity>>

    @Query("UPDATE lost_cases SET status = :status, closedAt = :closedAt WHERE caseId = :caseId")
    suspend fun updateStatus(caseId: String, status: String, closedAt: Long?)
}

@Dao
interface RecoverySightingDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(sighting: RecoverySightingEntity)

    @Query("SELECT * FROM recovery_sightings WHERE caseId = :caseId ORDER BY timestamp DESC")
    fun getSightingsForCase(caseId: String): Flow<List<RecoverySightingEntity>>

    @Query("SELECT * FROM recovery_sightings ORDER BY timestamp DESC")
    fun getAllSightings(): Flow<List<RecoverySightingEntity>>
}

@Dao
interface UserProfileDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(profile: UserProfileEntity)

    @Query("SELECT * FROM user_profiles WHERE userId = :userId")
    suspend fun getProfile(userId: String): UserProfileEntity?

    @Query("SELECT * FROM user_profiles WHERE userId = :userId")
    fun getProfileSync(userId: String): UserProfileEntity?

    @Query("SELECT * FROM user_profiles WHERE username = :username")
    suspend fun getProfileByUsername(username: String): UserProfileEntity?

    @Query("SELECT * FROM user_profiles")
    fun getAllProfiles(): Flow<List<UserProfileEntity>>
}

@Dao
interface RecoveryObservationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(observation: RecoveryObservationEntity)

    @Query("SELECT * FROM recovery_observations WHERE assetId = :assetId ORDER BY timestamp DESC")
    fun getObservationsForAsset(assetId: String): Flow<List<RecoveryObservationEntity>>

    @Query("SELECT * FROM recovery_observations ORDER BY timestamp DESC")
    fun getAllRecoveryObservations(): Flow<List<RecoveryObservationEntity>>
}
