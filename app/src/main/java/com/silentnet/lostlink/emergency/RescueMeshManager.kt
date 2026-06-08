package com.silentnet.lostlink.emergency

import com.silentnet.lostlink.repository.LostLinkRepository
import kotlinx.coroutines.flow.map

class RescueMeshManager(
    private val repository: LostLinkRepository
) {
    fun getActiveRescueBeacons() = repository.getActiveEmergencies()

    fun estimateDeliveryProbability(targetNodeId: String) = repository.getAnalytics().map {
        it?.deliveryConfidence ?: 0.5
    }

    fun getRelayDensity() = repository.getAllRelayStats().map { it.size }
}
