package com.silentnet.lostlink.analytics

import com.silentnet.lostlink.models.LostLinkDeliveryReport
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class DeliveryConfidenceEngine {
    private val _confidence = MutableStateFlow(0.0)
    val confidence = _confidence.asStateFlow()

    fun calculateConfidence(reports: List<LostLinkDeliveryReport>): Double {
        if (reports.isEmpty()) return 0.5
        
        val delivered = reports.count { it.status == "DELIVERED" }
        val expired = reports.count { it.status == "EXPIRED" }
        val forwarded = reports.count { it.status == "FORWARDED" }
        
        val score = (delivered * 1.0 + forwarded * 0.2) / (delivered + expired + forwarded).toDouble()
        _confidence.value = score
        return score
    }

    fun predictDeliveryProbability(): Double = _confidence.value
}
