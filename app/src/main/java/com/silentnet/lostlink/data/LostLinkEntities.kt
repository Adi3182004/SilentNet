package com.silentnet.lostlink.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "lostlink_sightings")
data class LostLinkSightingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val deviceId: String,
    val beaconId: String,
    val timestamp: Long,
    val relayNode: String,
    val signalStrength: Int,
    val batteryLevel: Int,
    val hopCount: Int,
    val lastSeen: Long
)

@Entity(tableName = "lostlink_beacons")
data class LostLinkBeaconEntity(
    @PrimaryKey val beaconId: String,
    val ownerId: String,
    val rotatedAt: Long,
    val isPrivate: Boolean,
    val isStealth: Boolean,
    val intervalMs: Long,
    val expiration: Long
)

@Entity(tableName = "lostlink_recovery")
data class LostLinkRecoveryEntity(
    @PrimaryKey val deviceId: String,
    val isLost: Boolean,
    val recoveryModeEnabled: Boolean,
    val lastKnownSightingId: Long?,
    val recoveryTimelineJson: String,
    val updatedAt: Long
)

@Entity(tableName = "lostlink_assets")
data class LostLinkAssetEntity(
    @PrimaryKey val assetId: String,
    val ownerId: String,
    val type: String, // Bag, Laptop, Phone, Bike, Custom
    val lastSeenTimestamp: Long,
    val lastRelayNode: String,
    val confidenceScore: Double
)

@Entity(tableName = "lostlink_relay")
data class LostLinkRelayEntity(
    @PrimaryKey val nodeId: String,
    val storedPackets: Int,
    val forwardedPackets: Int,
    val expiredPackets: Int,
    val successfulDeliveries: Int,
    val relayContributionScore: Double
)

@Entity(tableName = "lostlink_emergency")
data class LostLinkEmergencyEntity(
    @PrimaryKey val beaconId: String,
    val type: String, // SOS, Medical, Flood, Fire, Earthquake, Custom
    val priority: Int,
    val ttl: Int,
    val relayCount: Int,
    val createdAt: Long,
    val expirationTime: Long
)

@Entity(tableName = "lostlink_analytics")
data class LostLinkAnalyticsEntity(
    @PrimaryKey val id: Int = 1,
    val devicesTracked: Int,
    val beaconsObserved: Int,
    val recoverySuccessRate: Double,
    val relayParticipation: Double,
    val emergencyBeaconCount: Int,
    val deliveryConfidence: Double
)

@Entity(tableName = "lostlink_observations")
data class LostLinkObservationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val beaconId: String,
    val observerId: String,
    val firstSeen: Long,
    val lastSeen: Long,
    val observationCount: Int,
    val confidence: Double,
    val rssi: Int = -100,
    val locationHint: String?,
    val sourceType: String // BLE, Nearby, Mesh, etc.
)
