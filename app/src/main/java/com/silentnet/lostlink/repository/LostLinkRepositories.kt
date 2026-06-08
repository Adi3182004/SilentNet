package com.silentnet.lostlink.repository

import com.silentnet.lostlink.data.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class LostLinkRepository(
    private val sightingDao: LostLinkSightingDao,
    private val beaconDao: LostLinkBeaconDao,
    private val recoveryDao: LostLinkRecoveryDao,
    private val assetDao: LostLinkAssetDao,
    private val relayDao: LostLinkRelayDao,
    private val emergencyDao: LostLinkEmergencyDao,
    private val analyticsDao: LostLinkAnalyticsDao,
    private val observationDao: LostLinkObservationDao,
    private val userProfileDao: UserProfileDao,
    private val recoveryAssetDao: AssetDao,
    private val recoveryObservationDao: RecoveryObservationDao,
    private val trustedDeviceDao: TrustedDeviceDao,
    private val lostCaseDao: LostCaseDao,
    private val recoverySightingDao: RecoverySightingDao
) {
    // Trusted Device Registry (Phase 1)
    suspend fun saveTrustedDevice(device: TrustedDeviceEntity) = trustedDeviceDao.insert(device)
    suspend fun getTrustedDevice(deviceId: String) = trustedDeviceDao.getDevice(deviceId)
    fun getAllTrustedDevices() = trustedDeviceDao.getAllTrustedDevices()

    // Recovery Assets (Phase 2)
    suspend fun saveAsset(asset: AssetEntity) = recoveryAssetDao.insert(asset)
    suspend fun getAsset(assetId: String) = recoveryAssetDao.getAsset(assetId)
    suspend fun getAssetByLinkedDevice(deviceId: String) = recoveryAssetDao.getAssetByLinkedDevice(deviceId)
    fun getAssetsByOwner(ownerId: String) = recoveryAssetDao.getAssetsByOwner(ownerId)
    suspend fun updateAssetLostStatus(assetId: String, isLost: Boolean) = recoveryAssetDao.updateLostStatus(assetId, isLost)
    fun getLostAssets() = recoveryAssetDao.getLostAssets()
    fun searchAssets(query: String) = recoveryAssetDao.searchAssets(query)

    // Lost Cases (Phase 3)
    suspend fun saveLostCase(lostCase: LostCaseEntity) = lostCaseDao.insert(lostCase)
    suspend fun getActiveCaseForAsset(assetId: String) = lostCaseDao.getActiveCaseForAsset(assetId)
    fun getAllActiveCases() = lostCaseDao.getAllActiveCases()
    suspend fun updateLostCaseStatus(caseId: String, status: String, closedAt: Long?) = lostCaseDao.updateStatus(caseId, status, closedAt)

    // Recovery Sightings (Phase 4)
    suspend fun saveRecoverySighting(sighting: RecoverySightingEntity) = recoverySightingDao.insert(sighting)
    fun getSightingsForCase(caseId: String) = recoverySightingDao.getSightingsForCase(caseId)
    fun getAllRecoverySightings() = recoverySightingDao.getAllSightings()

    // User Profile
    suspend fun saveUserProfile(profile: UserProfileEntity) = userProfileDao.insert(profile)
    suspend fun getUserProfile(userId: String) = userProfileDao.getProfile(userId)
    fun getAllUserProfiles() = userProfileDao.getAllProfiles()
    suspend fun getProfileByUsername(username: String) = userProfileDao.getProfileByUsername(username)

    // Recovery Observations (Legacy/Internal)
    suspend fun saveRecoveryObservation(observation: RecoveryObservationEntity) = recoveryObservationDao.insert(observation)
    fun getObservationsForAsset(assetId: String) = recoveryObservationDao.getObservationsForAsset(assetId)
    fun getAllRecoveryObservations() = recoveryObservationDao.getAllRecoveryObservations()

    // Sighting Repository (Legacy/V1)
    suspend fun recordSighting(sighting: LostLinkSightingEntity) = sightingDao.insert(sighting)
    fun getSightingsForDevice(deviceId: String): Flow<List<LostLinkSightingEntity>> = sightingDao.getSightingsForDevice(deviceId)
    fun getAllSightings(): Flow<List<LostLinkSightingEntity>> = sightingDao.getAllSightings()

    // Beacon Repository (V1)
    suspend fun saveBeacon(beacon: LostLinkBeaconEntity) = beaconDao.insert(beacon)
    fun getBeaconsByOwner(ownerId: String): Flow<List<LostLinkBeaconEntity>> = beaconDao.getBeaconsByOwner(ownerId)


    // Recovery Repository (Legacy/V1)
    suspend fun saveRecoveryState(recovery: LostLinkRecoveryEntity) = recoveryDao.insert(recovery)
    suspend fun getRecoveryState(deviceId: String) = recoveryDao.getRecoveryState(deviceId)
    suspend fun updateLostStatus(deviceId: String, isLost: Boolean) = recoveryDao.updateLostStatus(deviceId, isLost, System.currentTimeMillis())

    // Relay Repository
    suspend fun saveRelayStats(relay: LostLinkRelayEntity) = relayDao.insert(relay)
    suspend fun getRelayStats(nodeId: String) = relayDao.getRelayStats(nodeId)
    fun getAllRelayStats() = relayDao.getAllRelayStats()

    // Emergency Repository
    suspend fun saveEmergency(emergency: LostLinkEmergencyEntity) = emergencyDao.insert(emergency)
    fun getActiveEmergencies() = emergencyDao.getActiveEmergencies(System.currentTimeMillis())

    // Analytics Repository
    suspend fun saveAnalytics(analytics: LostLinkAnalyticsEntity) = analyticsDao.insert(analytics)
    fun getAnalytics() = analyticsDao.getAnalytics()

    // Observation Repository
    suspend fun saveObservation(observation: LostLinkObservationEntity) = observationDao.insert(observation)
    suspend fun updateObservation(observation: LostLinkObservationEntity) = observationDao.update(observation)
    suspend fun getObservation(beaconId: String, observerId: String) = observationDao.getObservation(beaconId, observerId)
    fun getAllObservations(): Flow<List<LostLinkObservationEntity>> = observationDao.getAllObservations()
    suspend fun getObservationsByBeacon(beaconId: String) = observationDao.getObservationsByBeacon(beaconId)

    /**
     * Phase 2: Enriched observations with user profiles.
     */
    fun getEnrichedObservations(): Flow<List<EnrichedObservation>> {
        return observationDao.getAllObservations().map { observations ->
            observations.map { obs ->
                // Try to resolve beaconId to owner profile
                // 1. Check if beacon belongs to a known asset
                val asset = recoveryAssetDao.getAssetSync(obs.beaconId)
                // 2. Check if we have a profile for the owner
                val profile = if (asset != null) {
                    userProfileDao.getProfileSync(asset.ownerId)
                } else {
                    null
                }
                EnrichedObservation(obs, profile, asset)
            }
        }
    }
}

data class EnrichedObservation(
    val observation: LostLinkObservationEntity,
    val profile: UserProfileEntity?,
    val asset: AssetEntity?
)
