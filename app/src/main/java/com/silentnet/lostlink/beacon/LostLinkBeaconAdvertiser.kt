package com.silentnet.lostlink.beacon

import android.util.Log

import com.silentnet.lostlink.ble.BleAdvertiserManager

/**
 * LostLinkBeaconAdvertiser
 * 
 * Responsibility:
 * - Start/Stop beacon advertising.
 * - Update advertised data when beacon rotates.
 */
class LostLinkBeaconAdvertiser(private val bleManager: BleAdvertiserManager? = null) {
    private var isAdvertising = false
    private var currentPacket: LostLinkBeaconPacket? = null

    /**
     * Starts advertising the given packet.
     */
    fun start(packet: LostLinkBeaconPacket) {
        currentPacket = packet
        isAdvertising = true
        Log.d(TAG, "Started advertising: ${packet.beaconId} (v${packet.rotationVersion})")
        
        // Start real BLE advertising
        bleManager?.startAdvertising(packet)
    }

    /**
     * Updates the advertised beacon data.
     */
    fun update(packet: LostLinkBeaconPacket) {
        if (isAdvertising) {
            currentPacket = packet
            Log.d(TAG, "Updated advertisement: ${packet.beaconId} (v${packet.rotationVersion})")
            
            // Update real BLE advertising
            bleManager?.startAdvertising(packet)
        }
    }

    /**
     * Stops advertising.
     */
    fun stop() {
        isAdvertising = false
        currentPacket = null
        Log.d(TAG, "Stopped advertising")
        
        // Stop real BLE advertising
        bleManager?.stopAdvertising()
    }

    fun isActive(): Boolean = isAdvertising
    fun getCurrentPacket(): LostLinkBeaconPacket? = currentPacket

    companion object {
        private const val TAG = "LostLinkBeaconAdv"
    }
}
