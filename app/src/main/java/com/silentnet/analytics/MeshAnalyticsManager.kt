package com.silentnet.analytics

import com.silentnet.data.*
import com.silentnet.transport.TransportManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.text.SimpleDateFormat
import java.util.*

class MeshAnalyticsManager(
    private val transportManager: TransportManager,
    private val analyticsDao: AnalyticsDao,
    private val messageDao: MessageDao,
    private val meshPacketDao: MeshPacketDao,
    private val groupDao: GroupDao,
    private val recoveryDao: RecoveryDao,
    private val researchDao: ResearchDao,
    private val disasterDao: com.silentnet.data.DisasterDao
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    private val _dashboardStats = MutableStateFlow(DashboardStats())
    val dashboardStats: StateFlow<DashboardStats> = _dashboardStats.asStateFlow()
    
    private val _researchStats = MutableStateFlow(ResearchStats())
    val researchStats: StateFlow<ResearchStats> = _researchStats.asStateFlow()

    init {
        scope.launch {
            while (isActive) {
                updateDashboardStats()
                updateResearchStats()
                delay(5000) // Update every 5 seconds
            }
        }
    }

    private suspend fun updateDashboardStats() {
        val routingTable = transportManager.getRoutingTable()
        val activeConnections = transportManager.getActiveConnections()
        
        val activeNodes = routingTable.keys.size
        val directNeighbors = activeConnections.size
        val knownRoutes = routingTable.values.sumOf { it.size }
        
        val pendingPackets = meshPacketDao.getPacketCount()
        val emergencyCount = messageDao.getEmergencyCount()
        val groupCount = groupDao.getGroupCount()
        val recoveryPostCount = recoveryDao.getPostCount()

        val analytics = analyticsDao.getAnalyticsForDate(dateFormat.format(Date()))
        
        // Phase 12: Disaster Recovery Stats
        val activeIncidents = disasterDao.getIncidentCount()
        val missingPersons = disasterDao.getMissingPersonCount()
        val volunteersActive = disasterDao.getActiveVolunteerCount()
        val safeZonesOpen = disasterDao.getSafeZoneCount()
        val medicalRequests = disasterDao.getMedicalRequestCount()
        val resourcesAvailable = disasterDao.getResourceCount()

        val healthScore = computeHealthScore(activeNodes, knownRoutes, analytics)
        
        _dashboardStats.value = DashboardStats(
            activeNodes = activeNodes,
            directNeighbors = directNeighbors,
            knownRoutes = knownRoutes,
            connectedPeers = directNeighbors,
            pendingPackets = pendingPackets,
            storeForwardQueueSize = pendingPackets,
            emergencyQueueSize = emergencyCount,
            groupCount = groupCount,
            recoveryPostCount = recoveryPostCount,
            healthScore = healthScore,
            healthLabel = getHealthLabel(healthScore),
            activeIncidents = activeIncidents,
            missingPersons = missingPersons,
            volunteersActive = volunteersActive,
            safeZonesOpen = safeZonesOpen,
            medicalRequests = medicalRequests,
            resourcesAvailable = resourcesAvailable
        )
    }

    private suspend fun updateResearchStats() {
        val date = dateFormat.format(Date())
        val metrics = researchDao.getMetricsForDate(date) ?: ResearchMetricEntity(date)
        
        val routingTable = transportManager.getRoutingTable()
        val allRoutes = routingTable.values.flatMap { it.values }
        
        // Feature 5: Delivery Prediction (Average of all known routes)
        val avgDeliveryProb = if (allRoutes.isNotEmpty()) {
            allRoutes.sumOf { it.deliveryConfidence * it.reputationMultiplier } / allRoutes.size
        } else 0.0

        // Research: Route Stability
        val routeStability = if (allRoutes.isNotEmpty()) {
            allRoutes.sumOf { 1.0 / (1.0 + (System.currentTimeMillis() - it.lastSeen) / 3600000.0) } / allRoutes.size
        } else 0.0

        _researchStats.value = ResearchStats(
            gossipEfficiency = if (metrics.totalGossipPackets > 0) metrics.packetsSuppressed.toDouble() / metrics.totalGossipPackets else 1.0,
            deliveryPrediction = avgDeliveryProb,
            routeStability = routeStability,
            redundancyRate = 1.0 - (if (metrics.totalGossipPackets > 0) metrics.packetsSuppressed.toDouble() / metrics.totalGossipPackets else 0.0),
            relayLoadDistribution = computeLoadDistribution(allRoutes),
            congestionScore = (metrics.totalGossipPackets.toDouble() / 100.0).coerceAtMost(1.0)
        )
    }

    private fun computeLoadDistribution(routes: List<com.silentnet.transport.MeshRoute>): Double {
        if (routes.isEmpty()) return 1.0
        val loads = routes.map { it.relayContribution }.filter { it > 0 }
        if (loads.isEmpty()) return 1.0
        // Simple coefficient of variation approach
        val avg = loads.average()
        val stdDev = Math.sqrt(loads.map { Math.pow(it - avg, 2.0) }.average())
        return (1.0 - (stdDev / avg).coerceAtMost(1.0))
    }

    private fun computeHealthScore(activeNodes: Int, knownRoutes: Int, analytics: MeshAnalyticsEntity?): Double {
        if (activeNodes == 0) return 0.0
        
        val nodeFactor = (activeNodes.toDouble() / 10.0).coerceAtMost(1.0)
        val routeFactor = (knownRoutes.toDouble() / (activeNodes * 2.0).coerceAtLeast(1.0)).coerceAtMost(1.0)
        
        val successRate = if (analytics != null && analytics.messagesSent > 0) {
            analytics.messagesDelivered.toDouble() / analytics.messagesSent.toDouble()
        } else 1.0
        
        return (nodeFactor * 0.4 + routeFactor * 0.3 + successRate * 0.3) * 100.0
    }

    private fun getHealthLabel(score: Double): String {
        return when {
            score < 25 -> "Poor"
            score < 50 -> "Fair"
            score < 75 -> "Good"
            else -> "Excellent"
        }
    }

    data class DashboardStats(
        val activeNodes: Int = 0,
        val directNeighbors: Int = 0,
        val relayNodes: Int = 0,
        val knownRoutes: Int = 0,
        val connectedPeers: Int = 0,
        val pendingPackets: Int = 0,
        val storeForwardQueueSize: Int = 0,
        val emergencyQueueSize: Int = 0,
        val groupCount: Int = 0,
        val recoveryPostCount: Int = 0,
        val healthScore: Double = 0.0,
        val healthLabel: String = "Poor",
        val activeIncidents: Int = 0,
        val missingPersons: Int = 0,
        val volunteersActive: Int = 0,
        val safeZonesOpen: Int = 0,
        val medicalRequests: Int = 0,
        val resourcesAvailable: Int = 0
    )

    data class ResearchStats(
        val gossipEfficiency: Double = 0.0,
        val deliveryPrediction: Double = 0.0,
        val routeStability: Double = 0.0,
        val redundancyRate: Double = 0.0,
        val relayLoadDistribution: Double = 0.0,
        val congestionScore: Double = 0.0
    )
}
