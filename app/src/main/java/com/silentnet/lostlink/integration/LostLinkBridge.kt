package com.silentnet.lostlink.integration

import android.content.Context
import android.content.Intent
import android.util.Log
import com.silentnet.lostlink.LostLinkV2Manager
import com.silentnet.lostlink.services.LostLinkBleForegroundService

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * LostLink V2 Bridge
 * 
 * This bridge is the safe integration layer between SilentNet and LostLink V2.
 * It manages the lifecycle of the LostLink subsystem while maintaining strict isolation.
 */
class LostLinkBridge(private val context: Context) {

    private val prefs = context.getSharedPreferences("lostlink_prefs", Context.MODE_PRIVATE)
    
    private val _isEnabled = MutableStateFlow(prefs.getBoolean("enabled", false))
    
    /**
     * Observable state of the LostLink feature.
     */
    val isEnabled: StateFlow<Boolean> = _isEnabled.asStateFlow()

    /**
     * Central manager instance (lazy)
     */
    val manager: LostLinkV2Manager by lazy {
        LostLinkV2Manager.getInstance(context)
    }

    /**
     * Enable or disable LostLink subsystem
     */
    fun setLostLinkEnabled(enabled: Boolean) {
        Log.e("LOSTLINK_EXECUTION", "ENTER_SET_LOSTLINK_ENABLED enabled=$enabled")
        if (!enabled) {
            Log.e("LOSTLINK_FORENSICS", "DISABLE_CALLED_ON_BRIDGE", Throwable())
        }
        Log.d("LostLinkBridge", "setLostLinkEnabled enabled=$enabled")
        
        prefs.edit().putBoolean("enabled", enabled).apply()
        _isEnabled.value = enabled
        Log.e("LOSTLINK_STATE", "STATE_CHANGED value=$enabled")

        if (enabled) {
            startService()
        } else {
            stopService()
        }
    }

    /**
     * Start the LostLink BLE service
     */
    private fun startService() {
        Log.d("LostLinkBridge", "STARTING_SERVICE")
        Log.e("SERVICE_TRACE", "SERVICE_TRACE_1")
        Log.e("SERVICE_TRACE", "SERVICE_TRACE_2")
        val intent = Intent(context, LostLinkBleForegroundService::class.java)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            Log.e("SERVICE_TRACE", "SERVICE_TRACE_3")
            try {
                Log.e("SERVICE_EXCEPTION", "START_ATTEMPT")
                context.startForegroundService(intent)
                Log.e("SERVICE_EXCEPTION", "START_RETURNED")
            } catch (t: Throwable) {
                Log.e("SERVICE_EXCEPTION", "START_FAILED", t)
            }
            Log.e("SERVICE_TRACE", "SERVICE_TRACE_4")
        } else {
            context.startService(intent)
        }
    }

    /**
     * Stop the LostLink BLE service
     */
    private fun stopService() {
        Log.d("LostLinkBridge", "STOPPING_SERVICE")
        val intent = Intent(context, LostLinkBleForegroundService::class.java)
        context.stopService(intent)
    }

    // Existing methods kept for compatibility with UI if needed
    fun getConnectedPeerCount(): Int = 0
    fun getNearbyPeers() = emptyList<String>()
    fun isNetworkActive(): Boolean = isEnabled.value
    fun getMeshNodeCount(): Int = 0
    fun getDeliveryConfidence(targetNodeId: String): Double = 0.5
}
