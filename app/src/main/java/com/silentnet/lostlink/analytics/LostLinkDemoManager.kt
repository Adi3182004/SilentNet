package com.silentnet.lostlink.analytics

import com.silentnet.lostlink.data.*
import com.silentnet.lostlink.repository.EnrichedObservation
import com.silentnet.lostlink.repository.LostLinkRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.*

/**
 * LostLinkDemoManager
 * 
 * Manages the "Portfolio Demo Mode" for LostLink.
 * Generates realistic, professional-looking data for demonstrations.
 */
class LostLinkDemoManager(private val repository: LostLinkRepository) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    private val _isDemoMode = MutableStateFlow(false)
    val isDemoMode: StateFlow<Boolean> = _isDemoMode.asStateFlow()

    private val _simulatedAssets = MutableStateFlow<List<AssetEntity>>(emptyList())
    val simulatedAssets: StateFlow<List<AssetEntity>> = _simulatedAssets.asStateFlow()

    private val _simulatedRecoverySightings = MutableStateFlow<List<RecoverySightingEntity>>(emptyList())
    val simulatedRecoverySightings: StateFlow<List<RecoverySightingEntity>> = _simulatedRecoverySightings.asStateFlow()

    private val _simulatedEnriched = MutableStateFlow<List<EnrichedObservation>>(emptyList())
    val simulatedEnriched: StateFlow<List<EnrichedObservation>> = _simulatedEnriched.asStateFlow()

    private val _simulatedAnalytics = MutableStateFlow(DemoAnalytics())
    val simulatedAnalytics: StateFlow<DemoAnalytics> = _simulatedAnalytics.asStateFlow()

    // Add back the legacy field to avoid compilation errors if other parts of the code still use it
    private val _simulatedSightings = MutableStateFlow<List<RecoveryObservationEntity>>(emptyList())
    val simulatedSightings: StateFlow<List<RecoveryObservationEntity>> = _simulatedSightings.asStateFlow()

    private var demoJob: Job? = null

    data class DemoAnalytics(
        val devicesProtected: Int = 12,
        val recoverySuccessRate: String = "94.2%",
        val networkCoverage: String = "High Density",
        val communityNodes: Int = 1248,
        val discoveriesToday: Int = 42
    )

    fun setDemoMode(enabled: Boolean) {
        _isDemoMode.value = enabled
        if (enabled) {
            startDemo()
        } else {
            stopDemo()
        }
    }

    private fun startDemo() {
        demoJob?.cancel()
        demoJob = scope.launch {
            generateDemoData()
            while (isActive) {
                simulateActivity()
                delay(8000)
            }
        }
    }

    private fun stopDemo() {
        demoJob?.cancel()
        _simulatedAssets.value = emptyList()
        _simulatedRecoverySightings.value = emptyList()
        _simulatedEnriched.value = emptyList()
    }

    private fun generateDemoData() {
        val now = System.currentTimeMillis()
        
        // 1. Assets
        val assets = listOf(
            AssetEntity("demo-laptop", "CURRENT_USER", "Aditya Andhalkar's MacBook", "Laptop", null, isLost = false),
            AssetEntity("demo-wallet", "CURRENT_USER", "Aditya Andhalkar's Wallet", "Wallet", null, isLost = true),
            AssetEntity("demo-bike", "CURRENT_USER", "Specialized Tarmac Pune", "Bike", null, isLost = false),
            AssetEntity("demo-bag", "CURRENT_USER", "Peak Design Pune", "Bag", null, isLost = false),
            AssetEntity("demo-phone", "CURRENT_USER", "Aditya Andhalkar's Pixel 8", "Phone", null, isLost = false)
        )
        _simulatedAssets.value = assets

        // 2. Recovery History (Sightings)
        val sightings = mutableListOf<RecoverySightingEntity>()
        
        // Sighting for the lost wallet in Pune
        sightings.add(RecoverySightingEntity(
            sightingId = "S_1",
            caseId = "CASE_DEMO",
            reporterDeviceId = "Node_882",
            detectedDeviceId = "demo-wallet",
            timestamp = now - 450000,
            rssi = -62,
            latitude = 18.5204, // Pune
            longitude = 73.8567,
            confidence = 0.96f,
            reporterUsername = "Ada Phone"
        ))
        
        sightings.add(RecoverySightingEntity(
            sightingId = "S_2",
            caseId = "CASE_DEMO",
            reporterDeviceId = "Node_114",
            detectedDeviceId = "demo-wallet",
            timestamp = now - 1800000,
            rssi = -75,
            latitude = 18.5210,
            longitude = 73.8570,
            confidence = 0.88f,
            reporterUsername = "Pune Community Node"
        ))

        sightings.add(RecoverySightingEntity(
            sightingId = "S_3",
            caseId = "CASE_DEMO",
            reporterDeviceId = "Node_45",
            detectedDeviceId = "demo-wallet",
            timestamp = now - 7200000,
            rssi = -82,
            latitude = 18.5220,
            longitude = 73.8580,
            confidence = 0.75f,
            reporterUsername = "Relay Mesh Pune"
        ))

        _simulatedRecoverySightings.value = sightings
        
        // 3. Recent Discoveries (Enriched)
        val enriched = listOf(
            createEnriched("demo-node-1", "Ada Phone", "Pixel 7 Pune", -72, now - 15000),
            createEnriched("demo-node-2", "Pune Mesh Node", "Galaxy S23", -84, now - 45000),
            createEnriched("demo-asset-1", null, "Blue Backpack Pune", -68, now - 5000, isLost = true),
            createEnriched("demo-node-3", "Elena Rodriguez", "iPhone 15", -92, now - 120000),
            createEnriched("demo-asset-2", null, "Giant Mountain Bike", -75, now - 300000, isLost = true)
        )
        _simulatedEnriched.value = enriched

        // 4. Analytics
        _simulatedAnalytics.value = DemoAnalytics(
            devicesProtected = 8,
            recoverySuccessRate = "96.5%",
            networkCoverage = "Excellent",
            communityNodes = 2415,
            discoveriesToday = 67
        )
    }

    private fun createEnriched(id: String, username: String?, deviceName: String, rssi: Int, lastSeen: Long, isLost: Boolean = false): EnrichedObservation {
        return EnrichedObservation(
            observation = LostLinkObservationEntity(
                beaconId = id,
                observerId = "LOCAL_SCANNER",
                firstSeen = lastSeen - 60000,
                lastSeen = lastSeen,
                observationCount = (1..50).random(),
                confidence = 0.9,
                rssi = rssi,
                locationHint = null,
                sourceType = "BLE"
            ),
            profile = username?.let { UserProfileEntity(id, it, deviceName) },
            asset = if (username == null)
                AssetEntity(
                    assetId = id,
                    ownerId = "REMOTE_OWNER",
                    assetName = deviceName,
                    assetType = "Misc",
                    linkedDeviceId = null,
                    isLost = isLost
                )
            else null
        )
    }

    private fun simulateActivity() {
        val currentEnriched = _simulatedEnriched.value.toMutableList()
        if (currentEnriched.isNotEmpty()) {
            val index = currentEnriched.indices.random()
            val item = currentEnriched[index]
            val newObs = item.observation.copy(
                lastSeen = System.currentTimeMillis(),
                rssi = item.observation.rssi + (-2..2).random()
            )
            currentEnriched[index] = item.copy(observation = newObs)
            _simulatedEnriched.value = currentEnriched
        }
    }
}
