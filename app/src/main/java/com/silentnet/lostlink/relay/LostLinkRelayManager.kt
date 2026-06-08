package com.silentnet.lostlink.relay

import com.silentnet.lostlink.data.LostLinkRelayEntity
import com.silentnet.lostlink.repository.LostLinkRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class LostLinkRelayManager(
    private val repository: LostLinkRepository,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {
    fun trackRelayActivity(nodeId: String, status: String) {
        scope.launch {
            val stats = repository.getRelayStats(nodeId) ?: LostLinkRelayEntity(nodeId, 0, 0, 0, 0, 0.0)
            
            val updated = when (status) {
                "STORED" -> stats.copy(storedPackets = stats.storedPackets + 1)
                "FORWARDED" -> stats.copy(forwardedPackets = stats.forwardedPackets + 1)
                "EXPIRED" -> stats.copy(expiredPackets = stats.expiredPackets + 1)
                "DELIVERED" -> stats.copy(successfulDeliveries = stats.successfulDeliveries + 1)
                else -> stats
            }
            
            val score = calculateContributionScore(updated)
            repository.saveRelayStats(updated.copy(relayContributionScore = score))
        }
    }

    fun relayRecoverySighting(sighting: com.silentnet.lostlink.data.RecoverySightingEntity, relayNodeId: String) {
        scope.launch {
            val relayedSighting = sighting.copy(
                sightingId = java.util.UUID.randomUUID().toString(),
                hopCount = sighting.hopCount + 1,
                relayPath = (sighting.relayPath?.let { "$it -> " } ?: "") + relayNodeId
            )
            repository.saveRecoverySighting(relayedSighting)
            android.util.Log.e("RUNTIME_LOG", "RECOVERY_REPORT_RELAYED hopCount=${relayedSighting.hopCount} relayPath=${relayedSighting.relayPath}")
        }
    }

    private fun calculateContributionScore(stats: LostLinkRelayEntity): Double {
        return (stats.forwardedPackets * 1.0 + stats.successfulDeliveries * 5.0) / 
               (stats.storedPackets + stats.expiredPackets + 1.0)
    }
}
