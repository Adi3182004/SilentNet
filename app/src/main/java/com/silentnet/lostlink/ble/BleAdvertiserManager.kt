package com.silentnet.lostlink.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.Context
import android.os.ParcelUuid
import android.util.Log
import com.silentnet.lostlink.beacon.LostLinkBeaconCodec
import com.silentnet.lostlink.beacon.LostLinkBeaconPacket
import java.util.UUID

/**
 * BleAdvertiserManager
 * 
 * Responsibility:
 * - Direct interaction with Android BLE APIs for advertising.
 * - Manage advertising lifecycle.
 */
class BleAdvertiserManager(private val context: Context) {
    private val TAG = "BleAdvertiserManager"
    
    // LostLink 16-bit Service UUID
    private val SERVICE_UUID = UUID.fromString("0000FC00-0000-1000-8000-00805F9B34FB")
    
    private enum class AdvertisingState { IDLE, STARTING, ADVERTISING, STOPPING }
    private var state = AdvertisingState.IDLE
    private var lastPacket: LostLinkBeaconPacket? = null

    private var bluetoothLeAdvertiser: BluetoothLeAdvertiser? = null

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
            super.onStartSuccess(settingsInEffect)
            synchronized(this@BleAdvertiserManager) {
                state = AdvertisingState.ADVERTISING
                Log.i(TAG, "BLE Advertising started successfully (State: ADVERTISING)")
            }
        }

        override fun onStartFailure(errorCode: Int) {
            super.onStartFailure(errorCode)
            synchronized(this@BleAdvertiserManager) {
                state = AdvertisingState.IDLE
                lastPacket = null
                Log.e(TAG, "BLE Advertising failed with error code: $errorCode (State: IDLE)")
            }
        }
    }

    /**
     * Starts BLE advertising with the provided beacon packet.
     */
    @SuppressLint("MissingPermission")
    fun startAdvertising(packet: LostLinkBeaconPacket) {
        synchronized(this) {
            if (state == AdvertisingState.STARTING) {
                Log.d(TAG, "Start requested while STARTING. Ignoring duplicate request.")
                return
            }
            if (state == AdvertisingState.STOPPING) {
                Log.d(TAG, "Start requested while STOPPING. Ignoring.")
                return
            }
            if (state == AdvertisingState.ADVERTISING && packet == lastPacket) {
                Log.d(TAG, "Already advertising this packet. No change needed.")
                return
            }
            
            if (state == AdvertisingState.ADVERTISING) {
                Log.d(TAG, "New packet received while ADVERTISING. Stopping old session first.")
                stopAdvertisingInternal()
            }

            state = AdvertisingState.STARTING
            lastPacket = packet
        }

        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val adapter = bluetoothManager.adapter

        if (adapter == null || !adapter.isEnabled) {
            Log.e(TAG, "Bluetooth is disabled or not supported")
            synchronized(this) { state = AdvertisingState.IDLE; lastPacket = null }
            return
        }

        bluetoothLeAdvertiser = adapter.bluetoothLeAdvertiser
        
        // BLE_PROOF: Runtime parameter validation
        Log.d("BLE_PROOF", "--- START ADVERTISING PROOF ---")
        Log.d("BLE_PROOF", "SERVICE_UUID: $SERVICE_UUID")
        Log.d("BLE_PROOF", "Adapter Name: ${adapter.name ?: "Unknown"}")
        Log.d("BLE_PROOF", "Is Advertiser Null: ${bluetoothLeAdvertiser == null}")
        Log.d("BLE_PROOF", "Multiple Advertisement Supported: ${adapter.isMultipleAdvertisementSupported}")
        Log.d("BLE_PROOF", "Extended Advertising Supported: ${adapter.isLeExtendedAdvertisingSupported}")

        if (bluetoothLeAdvertiser == null) {
            Log.e(TAG, "Hardware does not support BLE Advertising (Peripheral Mode)")
            synchronized(this) { state = AdvertisingState.IDLE; lastPacket = null }
            return
        }

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_POWER)
            .setConnectable(false)
            .setTimeout(0)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_LOW)
            .build()
        
        Log.d("BLE_PROOF", "Settings - Mode: ${settings.mode}, TxPower: ${settings.txPowerLevel}, Connectable: ${settings.isConnectable}, Timeout: ${settings.timeout}")

        // Encode packet to ultra-compact binary format (15 bytes)
        val dataBytes = LostLinkBeaconCodec.encode(packet)
        val hexData = dataBytes.joinToString("") { "%02x".format(it) }
        
        Log.d("BLE_PROOF", "dataBytes Size: ${dataBytes.size}")
        Log.d("BLE_PROOF", "dataBytes Hex: $hexData")
        Log.d("BLE_PROOF", "--- END ADVERTISING PROOF ---")

        // Using 16-bit Service Data (AD Type 0x16)
        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .setIncludeTxPowerLevel(false)
            .addServiceData(ParcelUuid(SERVICE_UUID), dataBytes)
            .build()

        try {
            bluetoothLeAdvertiser?.startAdvertising(settings, data, advertiseCallback)
        } catch (e: Exception) {
            Log.e(TAG, "Exception starting BLE Advertising: ${e.message}")
            synchronized(this) { state = AdvertisingState.IDLE; lastPacket = null }
        }
    }

    /**
     * Stops BLE advertising.
     */
    @SuppressLint("MissingPermission")
    fun stopAdvertising() {
        synchronized(this) {
            stopAdvertisingInternal()
        }
    }

    private fun stopAdvertisingInternal() {
        // This method must only be called from a synchronized block or during teardown
        if (state == AdvertisingState.IDLE || state == AdvertisingState.STOPPING) return
        
        val prevState = state
        state = AdvertisingState.STOPPING
        try {
            bluetoothLeAdvertiser?.stopAdvertising(advertiseCallback)
            Log.d(TAG, "BLE Advertising stop requested (Previous State: $prevState)")
        } catch (e: Exception) {
            Log.e(TAG, "Exception stopping BLE Advertising: ${e.message}")
        } finally {
            state = AdvertisingState.IDLE
            lastPacket = null
        }
    }

    fun isAdvertising(): Boolean = synchronized(this) { state == AdvertisingState.ADVERTISING }
}
