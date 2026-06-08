package com.silentnet.lostlink.beacon

import android.content.Context
import android.util.Log
import com.silentnet.lostlink.ble.BleScannerManager
import com.silentnet.lostlink.observation.LostLinkObservationProcessor

/**
 * LostLinkBeaconScanner
 * 
 * Responsibility:
 * - Scan for beacon packets.
 * - Decode and validate packets.
 * - Pass valid packets to the ObservationProcessor.
 */
class LostLinkBeaconScanner(
    context: Context,
    private val processor: LostLinkObservationProcessor,
    private val stats: LostLinkBeaconStats? = null
) {
    private val bleScannerManager = BleScannerManager(context) { rawBytes, rssi, observerId ->
        onRawPacketDiscovered(rawBytes, rssi, observerId)
    }

    fun start() {
        bleScannerManager.startScanning()
        Log.d(TAG, "Beacon scanner started")
    }

    fun stop() {
        bleScannerManager.stopScanning()
        Log.d(TAG, "Beacon scanner stopped")
    }

    /**
     * Callback for when a raw packet is discovered by BleScannerManager.
     */
    internal fun onRawPacketDiscovered(rawBytes: ByteArray, rssi: Int, observerId: String) {
        val packet = LostLinkBeaconCodec.decode(rawBytes)
        if (packet == null) {
            Log.w(TAG, "Rejected malformed packet from $observerId")
            stats?.onBeaconRejected()
            return
        }

        val now = System.currentTimeMillis()
        if (packet.expiresAt < now) {
            Log.w(TAG, "Rejected expired packet: ${packet.beaconId}")
            stats?.onBeaconRejected()
            return
        }

        Log.d(TAG, "Valid packet received: ${packet.beaconId} (RSSI: $rssi)")
        stats?.onBeaconObserved()
        
        // Pass to processor
        processor.process(
            beaconId = packet.beaconId,
            observerId = observerId,
            createdAt = packet.createdAt,
            expiresAt = packet.expiresAt,
            rssi = rssi,
            sourceType = "LINK_V2"
        )
    }

    fun isScanning(): Boolean = bleScannerManager.isScanning()

    companion object {
        private const val TAG = "LostLinkBeaconScanner"
    }
}
