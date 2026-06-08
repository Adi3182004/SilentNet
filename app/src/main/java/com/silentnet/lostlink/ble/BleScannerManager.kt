package com.silentnet.lostlink.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.ParcelUuid
import android.util.Log
import java.util.UUID

/**
 * BleScannerManager
 * 
 * Responsibility:
 * - Direct interaction with Android BLE APIs for scanning.
 * - Filter for LostLink service UUID.
 * - Pass raw results back to a callback.
 */
class BleScannerManager(
    private val context: Context,
    private val onPacketDiscovered: (ByteArray, Int, String) -> Unit
) {
    private val TAG = "BleScannerManager"
    private val SERVICE_UUID = UUID.fromString("0000FC00-0000-1000-8000-00805F9B34FB")

    private var scanner: BluetoothLeScanner? = null
    private var isScanning = false

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val record = result.scanRecord ?: return
            val serviceData = record.getServiceData(ParcelUuid(SERVICE_UUID))
            
            if (serviceData != null) {
                onPacketDiscovered(serviceData, result.rssi, result.device.address)
            }
        }

        override fun onBatchScanResults(results: List<ScanResult>) {
            results.forEach { onScanResult(ScanSettings.CALLBACK_TYPE_ALL_MATCHES, it) }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e(TAG, "BLE Scan failed with error code: $errorCode")
            isScanning = false
        }
    }

    @SuppressLint("MissingPermission")
    fun startScanning() {
        if (isScanning) return

        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val adapter = bluetoothManager.adapter

        if (adapter == null || !adapter.isEnabled) {
            Log.e(TAG, "Bluetooth is disabled or not supported")
            return
        }

        scanner = adapter.bluetoothLeScanner
        if (scanner == null) {
            Log.e(TAG, "Hardware does not support BLE Scanning")
            return
        }

        val filters = listOf(
            ScanFilter.Builder()
                .setServiceData(ParcelUuid(SERVICE_UUID), null)
                .build()
        )

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_POWER)
            .build()

        try {
            scanner?.startScan(filters, settings, scanCallback)
            isScanning = true
            Log.i(TAG, "BLE Scanning started successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Exception starting BLE Scan: ${e.message}")
            isScanning = false
        }
    }

    @SuppressLint("MissingPermission")
    fun stopScanning() {
        if (!isScanning) return
        
        try {
            scanner?.stopScan(scanCallback)
            Log.i(TAG, "BLE Scanning stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Exception stopping BLE Scan: ${e.message}")
        } finally {
            isScanning = false
        }
    }

    fun isScanning(): Boolean = isScanning
}
