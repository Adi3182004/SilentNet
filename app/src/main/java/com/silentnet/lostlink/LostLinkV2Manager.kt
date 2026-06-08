package com.silentnet.lostlink

import android.content.Context
import com.silentnet.lostlink.analytics.DeliveryConfidenceEngine
import com.silentnet.lostlink.analytics.LostLinkAnalyticsManager
import com.silentnet.lostlink.beacon.BeaconRotationScheduler
import com.silentnet.lostlink.beacon.LostLinkBeaconAdvertiser
import com.silentnet.lostlink.beacon.LostLinkBeaconBroadcaster
import com.silentnet.lostlink.beacon.LostLinkBeaconManager
import com.silentnet.lostlink.beacon.LostLinkBeaconScanner
import com.silentnet.lostlink.beacon.LostLinkBeaconStats
import com.silentnet.lostlink.beacon.LostLinkBeaconTestHarness
import com.silentnet.lostlink.ble.BleAdvertiserManager
import com.silentnet.lostlink.database.LostLinkDatabase
import com.silentnet.lostlink.emergency.EmergencyBeaconManager
import com.silentnet.lostlink.emergency.RescueMeshManager
import com.silentnet.lostlink.observation.LostLinkObservationManager
import com.silentnet.lostlink.observation.LostLinkObservationProcessor
import com.silentnet.lostlink.platform.BlePermissionManager
import com.silentnet.lostlink.recovery.AssetTrackerManager
import com.silentnet.lostlink.recovery.LostDeviceManager
import com.silentnet.lostlink.recovery.LostDeviceSearchEngine
import com.silentnet.lostlink.relay.LostLinkRelayManager
import com.silentnet.lostlink.repository.LostLinkRepository

/**
 * LostLinkV2Manager
 * 
 * The central entry point for the isolated LostLink V2 system.
 * It owns and initializes all V2 sub-managers.
 */
class LostLinkV2Manager(context: Context) {
    
    private val database: LostLinkDatabase by lazy {
        LostLinkDatabase.getDatabase(context)
    }

    private val repository: LostLinkRepository by lazy {
        LostLinkRepository(
            database.sightingDao(),
            database.beaconDao(),
            database.recoveryDao(),
            database.assetDao(),
            database.relayDao(),
            database.emergencyDao(),
            database.analyticsDao(),
            database.observationDao(),
            database.userProfileDao(),
            database.recoveryAssetDao(),
            database.recoveryObservationDao(),
            database.trustedDeviceDao(),
            database.lostCaseDao(),
            database.recoverySightingDao()
        )
    }

    // Sub-Managers
    val beaconManager = LostLinkBeaconManager(repository)
    val recoveryManager = LostDeviceManager(repository)
    val assetManager = AssetTrackerManager(repository)
    val relayManager = LostLinkRelayManager(repository)
    val emergencyManager = EmergencyBeaconManager(repository)
    val rescueMeshManager = RescueMeshManager(repository)
    val confidenceEngine = DeliveryConfidenceEngine()
    val analyticsManager = LostLinkAnalyticsManager(repository, confidenceEngine)
    val demoManager = com.silentnet.lostlink.analytics.LostLinkDemoManager(repository)

    // Platform Components
    val blePermissionManager = BlePermissionManager(context)
    val bleAdvertiserManager = BleAdvertiserManager(context)

    // V2 Components
    val stats = LostLinkBeaconStats()
    val broadcaster = LostLinkBeaconBroadcaster()
    val advertiser = LostLinkBeaconAdvertiser(bleAdvertiserManager)
    val rotationScheduler = BeaconRotationScheduler(broadcaster, advertiser = advertiser, stats = stats)
    val observationManager = LostLinkObservationManager(repository, stats = stats)
    val observationProcessor = LostLinkObservationProcessor(observationManager)
    val scanner = LostLinkBeaconScanner(context, observationProcessor, stats = stats)
    val searchEngine = LostDeviceSearchEngine(repository)
    val testHarness = LostLinkBeaconTestHarness(scanner)

    /**
     * Observable flow of all discovered beacons.
     */
    val allObservations = repository.getAllObservations()

    /**
     * Phase 2: Enriched observations with names.
     */
    val allEnrichedObservations = repository.getEnrichedObservations()
    fun getObservationsForAsset(assetId: String) =
        repository.getObservationsForAsset(assetId)
    init {
        // Initialization logic for LostLink V2 only.
        // NO interaction with existing transport or messaging.
    }

    companion object {
        @Volatile
        private var INSTANCE: LostLinkV2Manager? = null

        fun getInstance(context: Context): LostLinkV2Manager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: LostLinkV2Manager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
