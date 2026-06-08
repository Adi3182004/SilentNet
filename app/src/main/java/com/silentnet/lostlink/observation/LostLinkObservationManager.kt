package com.silentnet.lostlink.observation

import android.util.Log
import com.silentnet.lostlink.data.LostLinkObservationEntity
import com.silentnet.lostlink.repository.LostLinkRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * LostLinkObservationManager
 * 
 * Responsibility:
 * - Receive observed beacons.
 * - Deduplicate observations.
 * - Update existing records.
 * - Maintain observation history.
 */
class LostLinkObservationManager(
    private val repository: LostLinkRepository,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO),
    private val stats: com.silentnet.lostlink.beacon.LostLinkBeaconStats? = null
) {
    private val TAG = "LostLinkObs"

    fun onBeaconObserved(
        beaconId: String,
        observerId: String,
        confidence: Double,
        rssi: Int,
        locationHint: String?,
        sourceType: String
    ) {
        Log.i("DISCOVERY_RECEIVED", "Beacon: $beaconId RSSI: $rssi Source: $sourceType")
        
        scope.launch {
            val existing = repository.getObservation(beaconId, observerId)
            val now = System.currentTimeMillis()

            if (existing != null) {
                // Update existing record
                val updated = existing.copy(
                    lastSeen = now,
                    observationCount = existing.observationCount + 1,
                    confidence = (existing.confidence + confidence) / 2.0,
                    rssi = rssi,
                    locationHint = locationHint ?: existing.locationHint,
                    sourceType = sourceType
                )
                repository.updateObservation(updated)
                Log.i("CONTACT_UPDATED", "Device: $beaconId (Seen: ${updated.observationCount} times)")
            } else {
                // Create new record
                val newObservation = LostLinkObservationEntity(
                    beaconId = beaconId,
                    observerId = observerId,
                    firstSeen = now,
                    lastSeen = now,
                    observationCount = 1,
                    confidence = confidence,
                    rssi = rssi,
                    locationHint = locationHint,
                    sourceType = sourceType
                )
                repository.saveObservation(newObservation)
                Log.i("CONTACT_CREATED", "New Device Discovered: $beaconId")
            }
            
            // 4. Update stats
            stats?.onObservationStored()

            // 5. Phase 5: Discovery Pipeline - Check for active Lost Cases
            try {
                // 5a. Find TrustedDevice
                val trustedDevice = repository.getTrustedDevice(beaconId)
                if (trustedDevice != null) {
                    // 5b. Find linked Asset
                    val asset = repository.getAssetByLinkedDevice(beaconId)
                    if (asset != null) {
                        // 5c. Find active LostCase
                        val activeCase = repository.getActiveCaseForAsset(asset.assetId)
                        if (activeCase != null) {
                            Log.e("RECOVERY_SIGHTING", "ACTIVE_LOST_CASE_DETECTED assetId=${asset.assetId} caseId=${activeCase.caseId}")
                            
                            val sighting = com.silentnet.lostlink.data.RecoverySightingEntity(
                                sightingId = java.util.UUID.randomUUID().toString(),
                                caseId = activeCase.caseId,
                                reporterDeviceId = observerId,
                                detectedDeviceId = beaconId,
                                timestamp = now,
                                latitude = 18.5204, // Demo coordinate: Pune
                                longitude = 73.8567,
                                rssi = rssi,
                                confidence = confidence.toFloat(),
                                reporterUsername = "Node_${observerId.take(4)}"
                            )
                            repository.saveRecoverySighting(sighting)
                            Log.e("RUNTIME_LOG", "RECOVERY_SIGHTING_CREATED caseId=${activeCase.caseId}")
                            Log.e("RUNTIME_LOG", "RECOVERY_LOCATION_CAPTURED lat=${sighting.latitude} lon=${sighting.longitude}")
                            Log.e("RUNTIME_LOG", "RECOVERY_TIMELINE_UPDATED caseId=${activeCase.caseId}")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("RECOVERY_PIPELINE", "Error checking lost cases", e)
            }
        }
    }
}
