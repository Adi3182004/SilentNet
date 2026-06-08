package com.silentnet.lostlink.beacon

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * LostLinkBeaconStats
 * 
 * Responsibility:
 * - Collect and provide runtime statistics for the Beacon subsystem.
 */
class LostLinkBeaconStats {
    data class Stats(
        val activeBeaconId: String? = null,
        val beaconRotations: Int = 0,
        val observedBeacons: Int = 0,
        val rejectedBeacons: Int = 0,
        val storedObservations: Int = 0
    )

    private val _stats = MutableStateFlow(Stats())
    val stats = _stats.asStateFlow()

    fun onBeaconRotated(beaconId: String) {
        _stats.update { it.copy(activeBeaconId = beaconId, beaconRotations = it.beaconRotations + 1) }
    }

    fun onBeaconObserved() {
        _stats.update { it.copy(observedBeacons = it.observedBeacons + 1) }
    }

    fun onBeaconRejected() {
        _stats.update { it.copy(rejectedBeacons = it.rejectedBeacons + 1) }
    }

    fun onObservationStored() {
        _stats.update { it.copy(storedObservations = it.storedObservations + 1) }
    }
}
