package com.silentnet.transport

import android.bluetooth.BluetoothAdapter
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.*
import android.content.Context
import java.util.UUID
import android.os.ParcelUuid
import android.util.Log
import com.silentnet.app.AppGraph
import com.silentnet.data.LostLinkReportEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.*

class LostLinkManager(
    private val context: Context,
    private val graph: AppGraph
) {
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val adapter: BluetoothAdapter? = bluetoothManager.adapter
    private val scanner: BluetoothLeScanner? = adapter?.bluetoothLeScanner
    private val advertiser: BluetoothLeAdvertiser? = adapter?.bluetoothLeAdvertiser
    
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var rotationJob: kotlinx.coroutines.Job? = null
    
    // Custom UUID for SilentNet LostLink Service
    private val LOSTLINK_SERVICE_UUID =
        ParcelUuid.fromString("8f4c1d23-7b18-4a52-9f41-6d8b7e2c9a11")

    fun startLostBeacon(deviceId: String) {
        scope.launch {
            val device = graph.recoveryRepository.findLostDeviceById(deviceId) ?: return@launch
            
            // Stop any existing rotation
            rotationJob?.cancel()
            
            rotationJob = scope.launch {
                while (true) {
                    val anonId = generateAnonymousId(device.deviceId, device.secret)
                    graph.recoveryRepository.updateAnonymousId(device.deviceId, anonId)
                    
                    updateAdvertisement(anonId)
                    Log.d("LostLink", "Beacon rotated for ${device.deviceName}. New Anon ID: $anonId")
                    
                    kotlinx.coroutines.delay(15 * 60 * 1000) // Rotate every 15 mins
                }
            }
        }
    }
    @SuppressLint("MissingPermission")
    private fun updateAdvertisement(anonymousId: String) {
        val advertiser = advertiser ?: return
        
        // Stop previous before starting new one with updated data
        advertiser.stopAdvertising(advertiseCallback)

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_POWER)
            .setConnectable(false)
            .setTimeout(0)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
            .build()

        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .addServiceUuid(LOSTLINK_SERVICE_UUID)
            .addServiceData(LOSTLINK_SERVICE_UUID, anonymousId.toByteArray(Charsets.UTF_8))
            .build()

        try {
            advertiser.startAdvertising(settings, data, advertiseCallback)
            Log.d("LostLink", "Lost beacon advertisement updated: $anonymousId")
        } catch (e: Exception) {
            Log.e("LostLink", "Failed to start advertising", e)
        }
    }

    private fun generateAnonymousId(deviceId: String, secret: String): String {
        val timeBucket = System.currentTimeMillis() / (15 * 60 * 1000)
        val data = "$deviceId:$secret:$timeBucket"
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(data.toByteArray(Charsets.UTF_8))
        return hash.joinToString("") { "%02x".format(it) }.take(8)
    }
    @SuppressLint("MissingPermission")
    fun stopLostBeacon() {
        rotationJob?.cancel()
        rotationJob = null
        advertiser?.stopAdvertising(advertiseCallback)
        Log.d("LostLink", "Lost beacon advertisement stopped")
    }
    @SuppressLint("MissingPermission")
    fun startScanning() {
        val scanner = scanner ?: return
        val filters = listOf(
            ScanFilter.Builder()
                .setServiceUuid(LOSTLINK_SERVICE_UUID)
                .build()
        )
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_POWER)
            .build()

        try {
            scanner.startScan(filters, settings, scanCallback)
            Log.d("LostLink", "LostLink BLE scanning started")
        } catch (e: Exception) {
            Log.e("LostLink", "Failed to start scanning", e)
        }
    }
    @SuppressLint("MissingPermission")
    fun stopScanning() {
        scanner?.stopScan(scanCallback)
        Log.d("LostLink", "LostLink BLE scanning stopped")
    }

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            Log.d("LostLink", "Lost beacon started success")
        }
        override fun onStartFailure(errorCode: Int) {
            Log.e("LostLink", "Lost beacon failure: $errorCode")
        }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val scanRecord = result.scanRecord ?: return
            val serviceData = scanRecord.serviceData[LOSTLINK_SERVICE_UUID]
            if (serviceData != null) {
                val foundDeviceId = String(serviceData, Charsets.UTF_8)
                handleDiscovery(foundDeviceId, result.rssi)
            }
        }
    }

    private fun handleDiscovery(deviceId: String, rssi: Int) {
        scope.launch {
            Log.d("LostLink", "Community Discovery: Device $deviceId seen with RSSI $rssi")
            val myNodeId = graph.sessionManager.currentUsername() ?: "Unknown"
            val reports = graph.recoveryRepository.getLostReports(deviceId)
            val confidence = graph.recoveryRepository.calculateRecoveryConfidence(reports)
            
            val report = LostLinkReportEntity(
                deviceId = deviceId,
                reporterNodeId = myNodeId,
                timestamp = System.currentTimeMillis(),
                rssi = rssi,
                confidence = confidence,
                nodeInfo = "Mesh Node ${myNodeId.take(6)}"
            )
            graph.recoveryRepository.insertLostReport(report)
            graph.transportManager.relayLostLinkReport(report)
        }
    }
}
