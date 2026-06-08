package com.silentnet.lostlink.observation

import android.util.Log

/**
 * LostLinkObservationProcessor
 * 
 * Responsibility:
 * - Validate observed beacon packets.
 * - Reject expired or malformed beacons.
 * - Calculate confidence scores for observations.
 * - Trigger storage via ObservationManager.
 */
class LostLinkObservationProcessor(
    private val observationManager: LostLinkObservationManager
) {
    
    /**
     * Processes a raw beacon observation.
     */
    fun process(
        beaconId: String,
        observerId: String,
        createdAt: Long,
        expiresAt: Long,
        rssi: Int,
        sourceType: String
    ) {
        val now = System.currentTimeMillis()

        // 0. Ownership Proof Log
        Log.e("LOSTLINK_OWNERSHIP", "DISCOVERED_BEACON_ID=$beaconId")

        // 1. Validate beacon
        if (beaconId.isBlank()) return
        if (expiresAt > 0 && expiresAt < now) {
            // Expired beacon
            return
        }

        // 2. Calculate confidence score based on RSSI and age
        // RSSI usually ranges from -100 (weak) to -30 (strong)
        val rssiConfidence = ((rssi + 100).toDouble() / 70.0).coerceIn(0.0, 1.0)
        
        // Age confidence: Newer beacons are more reliable
        val ageConfidence = if (expiresAt > createdAt && createdAt > 0) {
            val ttl = expiresAt - createdAt
            val age = now - createdAt
            (1.0 - (age.toDouble() / ttl.toDouble())).coerceIn(0.0, 1.0)
        } else {
            0.5
        }

        val finalConfidence = (rssiConfidence * 0.7) + (ageConfidence * 0.3)

        // 3. Store observation
        observationManager.onBeaconObserved(
            beaconId = beaconId,
            observerId = observerId,
            confidence = finalConfidence,
            rssi = rssi,
            locationHint = null, // Future: Add location integration
            sourceType = sourceType
        )
    }
}
