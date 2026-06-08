package com.silentnet.lostlink.beacon

import java.util.UUID

/**
 * LostLinkBeaconBroadcaster
 * 
 * Responsibility:
 * - Generate rotating beacon identifiers.
 * - Store active beacon.
 * - Provide beacon payload.
 */
class LostLinkBeaconBroadcaster {
    
    data class BeaconPayload(
        val beaconId: String,
        val rotationVersion: Int,
        val createdAt: Long,
        val expiresAt: Long
    )

    private var activeBeacon: BeaconPayload? = null

    /**
     * Generates a new rotating beacon identifier.
     */
    fun rotate(version: Int, ttlMs: Long): BeaconPayload {
        val now = System.currentTimeMillis()
        val payload = BeaconPayload(
            beaconId = LostLinkBeaconCodec.toCompactId(UUID.randomUUID()),
            rotationVersion = version,
            createdAt = now,
            expiresAt = now + ttlMs
        )
        activeBeacon = payload
        android.util.Log.e("LOSTLINK_OWNERSHIP", "MY_BEACON_ID=${payload.beaconId}")
        return payload
    }

    fun getActiveBeacon(): BeaconPayload? = activeBeacon
}
