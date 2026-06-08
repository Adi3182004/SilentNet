package com.silentnet.lostlink.beacon

import android.util.Log
import java.util.UUID

/**
 * LostLinkBeaconTestHarness
 * 
 * Responsibility:
 * - Simulate beacon transmission and reception.
 * - Test the observation pipeline without physical hardware.
 */
class LostLinkBeaconTestHarness(
    private val scanner: LostLinkBeaconScanner
) {

    /**
     * Simulates the discovery of a beacon.
     */
    fun simulateSighting(
        beaconId: String = LostLinkBeaconCodec.toCompactId(UUID.randomUUID()),
        rssi: Int = -60,
        observerId: String = "sim_node_1",
        ttlMs: Long = 900000
    ) {
        val now = System.currentTimeMillis()
        val packet = LostLinkBeaconPacket(
            beaconId = beaconId,
            rotationVersion = 1,
            createdAt = now,
            expiresAt = now + ttlMs,
            protocolVersion = 3
        )

        val rawBytes = LostLinkBeaconCodec.encode(packet)
        Log.i(TAG, "Simulating sighting of beacon: $beaconId")
        scanner.onRawPacketDiscovered(rawBytes, rssi, observerId)
    }

    /**
     * Simulates an expired beacon sighting.
     */
    fun simulateExpiredSighting(beaconId: String = LostLinkBeaconCodec.toCompactId(UUID.randomUUID())) {
        val now = System.currentTimeMillis()
        val packet = LostLinkBeaconPacket(
            beaconId = beaconId,
            rotationVersion = 1,
            createdAt = now - 2000000,
            expiresAt = now - 1000000,
            protocolVersion = 3
        )

        val rawBytes = LostLinkBeaconCodec.encode(packet)
        Log.i(TAG, "Simulating sighting of EXPIRED beacon: $beaconId")
        scanner.onRawPacketDiscovered(rawBytes, -80, "sim_node_expired")
    }

    /**
     * Simulates a malformed packet sighting.
     */
    fun simulateMalformedSighting() {
        Log.i(TAG, "Simulating sighting of MALFORMED packet")
        scanner.onRawPacketDiscovered(byteArrayOf(0x00, 0x01, 0x02), -90, "sim_node_malformed")
    }

    companion object {
        private const val TAG = "LostLinkTestHarness"
    }
}
