package com.silentnet.analytics

import com.silentnet.data.NetworkEventEntity
import com.silentnet.transport.MeshRoute
import com.silentnet.transport.NearbyPeer
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.*

class MeshDemoManager {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    private val _isDemoMode = MutableStateFlow(false)
    val isDemoMode: StateFlow<Boolean> = _isDemoMode.asStateFlow()

    private val _simulatedPeers = MutableStateFlow<List<NearbyPeer>>(emptyList())
    val simulatedPeers: StateFlow<List<NearbyPeer>> = _simulatedPeers.asStateFlow()

    private val _simulatedRoutes = MutableStateFlow<Map<String, Map<String, MeshRoute>>>(emptyMap())
    val simulatedRoutes: StateFlow<Map<String, Map<String, MeshRoute>>> = _simulatedRoutes.asStateFlow()

    private val _simulatedEvents = MutableSharedFlow<NetworkEventEntity>(replay = 0)
    val simulatedEvents: SharedFlow<NetworkEventEntity> = _simulatedEvents.asSharedFlow()

    private val _simulatedStats = MutableStateFlow(SimulatedStats())
    val simulatedStats: StateFlow<SimulatedStats> = _simulatedStats.asStateFlow()

    private val _disasterStats = MutableStateFlow(SimulatedDisasterStats())
    val disasterStats: StateFlow<SimulatedDisasterStats> = _disasterStats.asStateFlow()

    private var demoJob: Job? = null

    data class SimulatedStats(
        val nodeCount: Int = 0,
        val neighborCount: Int = 0,
        val routeCount: Int = 0,
        val packetCount: Int = 0,
        val healthScore: Double = 0.0,
        val gossipEfficiency: Double = 0.0,
        val deliveryPrediction: Double = 0.0,
        val routeStability: Double = 0.0,
        val relayLoadBalance: Double = 0.0
    )

    data class SimulatedDisasterStats(
        val activeIncidents: Int = 0,
        val missingPersons: Int = 0,
        val volunteersActive: Int = 0,
        val safeZonesOpen: Int = 0,
        val medicalRequests: Int = 0,
        val resourcesAvailable: Int = 0
    )

    fun setDemoMode(enabled: Boolean, nodeCount: Int = 10) {
        _isDemoMode.value = enabled
        if (enabled) {
            startDemo(nodeCount)
        } else {
            stopDemo()
        }
    }

    private fun startDemo(nodeCount: Int) {
        demoJob?.cancel()
        demoJob = scope.launch {
            generateNodes(nodeCount)
            while (isActive) {
                simulateActivity()
                delay(3000)
            }
        }
        logDemoEvent("Demo Started", "Simulated mesh with $nodeCount nodes initialized.")
    }

    private fun stopDemo() {
        demoJob?.cancel()
        _simulatedPeers.value = emptyList()
        _simulatedRoutes.value = emptyMap()
        logDemoEvent("Demo Stopped", "Simulated mesh destroyed.")
    }

    private fun generateNodes(count: Int) {
        val nodes = mutableListOf<NearbyPeer>()
        val usernames = listOf("Alice", "Bob", "Charlie", "David", "Eve", "Frank", "Grace", "Heidi", "Ivan", "Judy", "Mallory", "Niaj", "Oscar", "Peggy", "Sybil", "Trent", "Victor", "Walter")
        
        for (i in 1..count) {
            val id = "SIM-$i"
            val name = usernames.getOrElse(i % usernames.size) { "SimNode-$i" }
            nodes.add(NearbyPeer(
                endpointId = "EP-$id",
                username = name,
                fullName = "$name (Simulated)",
                nodeId = id
            ))
        }
        _simulatedPeers.value = nodes
        generateTopology(nodes)
    }

    private fun generateTopology(nodes: List<NearbyPeer>) {
        val routes = mutableMapOf<String, MutableMap<String, MeshRoute>>()
        
        // Simple Mesh Topology: Each node connects to 2-3 neighbors
        nodes.forEachIndexed { index, peer ->
            val nodeId = peer.nodeId ?: return@forEachIndexed
            val nodeRoutes = mutableMapOf<String, MeshRoute>()
            
            // Connect to next and previous nodes for a ring
            val neighbors = mutableListOf<Int>()
            neighbors.add((index + 1) % nodes.size)
            neighbors.add((index + nodes.size - 1) % nodes.size)
            
            // Add some random cross-links
            if (nodes.size > 4) {
                neighbors.add((index + nodes.size / 2) % nodes.size)
            }

            neighbors.distinct().forEach { neighborIndex ->
                val neighbor = nodes[neighborIndex]
                val neighborId = neighbor.nodeId ?: return@forEach
                nodeRoutes[neighborId] = MeshRoute(
                    targetNodeId = neighborId,
                    nextHopNodeId = neighborId,
                    nextHopEndpointId = neighbor.endpointId,
                    hopCount = 1,
                    successCount = (80..100).random(),
                    lastSeen = System.currentTimeMillis()
                )
            }
            routes[nodeId] = nodeRoutes
        }
        _simulatedRoutes.value = routes
    }

    private suspend fun simulateActivity() {
        val currentPeers = _simulatedPeers.value
        if (currentPeers.isEmpty()) return

        // Update simulated stats
        _simulatedStats.value = SimulatedStats(
            nodeCount = currentPeers.size,
            neighborCount = (currentPeers.size * 0.3).toInt().coerceAtLeast(2),
            routeCount = currentPeers.size * 2,
            packetCount = (Math.random() * 20).toInt(),
            healthScore = 85.0 + (Math.random() * 10),
            gossipEfficiency = 0.75 + (Math.random() * 0.2),
            deliveryPrediction = 0.7 + (Math.random() * 0.25),
            routeStability = 0.85 + (Math.random() * 0.1),
            relayLoadBalance = 0.8 + (Math.random() * 0.15)
        )

        _disasterStats.value = SimulatedDisasterStats(
            activeIncidents = (1..5).random(),
            missingPersons = (5..20).random(),
            volunteersActive = (10..50).random(),
            safeZonesOpen = (2..6).random(),
            medicalRequests = (3..12).random(),
            resourcesAvailable = (8..25).random()
        )

        val type = (0..6).random()
        when (type) {
            0 -> { // Node Joined/Left
                val leave = (0..1).random() == 0
                if (leave && currentPeers.size > 5) {
                    val peer = currentPeers.random()
                    _simulatedPeers.value = currentPeers - peer
                    logDemoEvent("Node Left", "Simulated node ${peer.username} left the mesh.")
                } else if (!leave && currentPeers.size < 50) {
                    val id = "SIM-${System.currentTimeMillis() % 1000}"
                    val peer = NearbyPeer(endpointId = "EP-$id", username = "Guest-$id", fullName = "Guest $id", nodeId = id)
                    _simulatedPeers.value = currentPeers + peer
                    logDemoEvent("Node Joined", "Simulated node ${peer.username} joined the mesh.")
                }
            }
            1 -> { // Packet Flow
                val src = currentPeers.random()
                val dst = currentPeers.random()
                if (src != dst) {
                    logDemoEvent("Packet Delivered", "Simulated message from ${src.username} to ${dst.username} delivered (2 hops).")
                }
            }
            2 -> { // Emergency
                val src = currentPeers.random()
                logDemoEvent("Emergency Broadcast", "Simulated Emergency from ${src.username}: 'Help needed at sector ${('A'..'F').random()}${(1..9).random()}'")
            }
            3 -> { // Route Updated
                val peer = currentPeers.random()
                logDemoEvent("Route Updated", "Simulated route to ${peer.username} optimized.")
            }
            4 -> { // Disaster Incident
                val types = listOf("Flood", "Fire", "Earthquake", "Building Collapse")
                logDemoEvent("Disaster Incident", "Simulated ${types.random()} report received at sector ${('A'..'F').random()}${(1..9).random()}")
            }
            5 -> { // Missing Person
                logDemoEvent("Missing Person", "Simulated Missing Person report for 'Simulated User ${(100..999).random()}' disseminated.")
            }
            6 -> { // Volunteer Active
                logDemoEvent("Volunteer Active", "Simulated Volunteer 'Node-${(1..20).random()}' registered for Rescue skills.")
            }
        }
    }

    private fun logDemoEvent(type: String, details: String) {
        scope.launch {
            _simulatedEvents.emit(NetworkEventEntity(type = type, details = "[DEMO] $details"))
        }
    }
}
