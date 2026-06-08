package com.silentnet.lostlink.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * TrustedDeviceEntity
 * 
 * Responsibility:
 * - Store devices that have been paired and trusted for recovery.
 */
@Entity(tableName = "trusted_devices")
data class TrustedDeviceEntity(
    @PrimaryKey val deviceId: String,
    val userId: String,
    val username: String,
    val deviceName: String,
    val deviceType: String,
    val publicKey: String,
    val registrationTime: Long = System.currentTimeMillis()
)

/**
 * AssetEntity
 * 
 * Responsibility:
 * - Store details of registered assets.
 */
@Entity(tableName = "recovery_assets")
data class AssetEntity(
    @PrimaryKey val assetId: String,
    val ownerId: String,
    val assetName: String,
    val assetType: String, // Phone, Laptop, Tablet, Watch, Wallet, Bag, Pet, Custom
    val linkedDeviceId: String?,
    val createdAt: Long = System.currentTimeMillis(),
    val isLost: Boolean = false // Added for convenience in UI, though LostCase is the source of truth
)

/**
 * LostCaseEntity
 * 
 * Responsibility:
 * - Track active recovery cases for lost assets.
 */
@Entity(tableName = "lost_cases")
data class LostCaseEntity(
    @PrimaryKey val caseId: String,
    val assetId: String,
    val openedAt: Long = System.currentTimeMillis(),
    val closedAt: Long? = null,
    val status: String // ACTIVE, FOUND, ARCHIVED
)

/**
 * RecoverySightingEntity
 * 
 * Responsibility:
 * - Store detailed sightings of lost assets.
 */
@Entity(tableName = "recovery_sightings")
data class RecoverySightingEntity(
    @PrimaryKey val sightingId: String,
    val caseId: String,
    val reporterDeviceId: String,
    val detectedDeviceId: String,
    val timestamp: Long = System.currentTimeMillis(),
    val latitude: Double?,
    val longitude: Double?,
    val rssi: Int,
    val confidence: Float,
    val hopCount: Int = 0,
    val reporterUsername: String,
    val relayPath: String? = null // For Phase 10: Demo Relay Mode
)

/**
 * UserProfileEntity (Legacy/Base)
 */
@Entity(
    tableName = "user_profiles",
    indices = [Index(value = ["username"], unique = true)]
)
data class UserProfileEntity(
    @PrimaryKey val userId: String,
    val username: String,
    val deviceName: String,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * RecoveryObservationEntity (Legacy/Internal)
 */
@Entity(tableName = "recovery_observations")
data class RecoveryObservationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val assetId: String,
    val observerId: String,
    val timestamp: Long,
    val rssi: Int,
    val latitude: Double?,
    val longitude: Double?,
    val confidence: Float = 0f
)
