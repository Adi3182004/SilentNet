package com.silentnet.lostlink.recovery

import com.silentnet.lostlink.data.LostLinkObservationEntity
import com.silentnet.lostlink.repository.LostLinkRepository
import kotlinx.coroutines.flow.firstOrNull

/**
 * LostDeviceSearchEngine
 * 
 * Responsibility:
 * - Search observations for specific beacons.
 * - Identify latest, strongest, and most frequent sightings.
 * - Generate recovery reports.
 */
class LostDeviceSearchEngine(
    val repository: LostLinkRepository
) {
    
    data class RecoveryReport(
        val beaconId: String,
        val latestSighting: LostLinkObservationEntity?,
        val strongestSighting: LostLinkObservationEntity?,
        val totalObservations: Int,
        val bestConfidence: Double
    )

    /**
     * Finds the latest sighting of a specific beacon.
     */
    suspend fun findLatestSighting(beaconId: String): LostLinkObservationEntity? {
        return repository.getObservationsByBeacon(beaconId).maxByOrNull { it.lastSeen }
    }

    /**
     * Generates a comprehensive recovery report for a beacon.
     */
    suspend fun generateRecoveryReport(beaconId: String): RecoveryReport {
        val observations = repository.getObservationsByBeacon(beaconId)
        
        return RecoveryReport(
            beaconId = beaconId,
            latestSighting = observations.maxByOrNull { it.lastSeen },
            strongestSighting = observations.maxByOrNull { it.confidence },
            totalObservations = observations.sumOf { it.observationCount },
            bestConfidence = observations.maxOfOrNull { it.confidence } ?: 0.0
        )
    }

    /**
     * Identifies the most observed beacon in the database.
     */
    suspend fun findMostObservedBeacon(): String? {
        val all = repository.getAllObservations().firstOrNull() ?: return null
        return all.groupBy { it.beaconId }
            .maxByOrNull { entry -> entry.value.sumOf { it.observationCount } }
            ?.key
    }
}
