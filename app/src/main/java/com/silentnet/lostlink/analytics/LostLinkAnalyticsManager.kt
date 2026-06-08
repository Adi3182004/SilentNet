package com.silentnet.lostlink.analytics

import com.silentnet.lostlink.data.LostLinkAnalyticsEntity
import com.silentnet.lostlink.repository.LostLinkRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class LostLinkAnalyticsManager(
    private val repository: LostLinkRepository,
    private val confidenceEngine: DeliveryConfidenceEngine,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {
    fun updateAnalytics(devices: Int, beacons: Int, successRate: Double) {
        scope.launch {
            repository.saveAnalytics(
                LostLinkAnalyticsEntity(
                    devicesTracked = devices,
                    beaconsObserved = beacons,
                    recoverySuccessRate = successRate,
                    relayParticipation = 0.8, // Example static metric
                    emergencyBeaconCount = 0,
                    deliveryConfidence = confidenceEngine.predictDeliveryProbability()
                )
            )
        }
    }

    fun getAnalytics() = repository.getAnalytics()
}
