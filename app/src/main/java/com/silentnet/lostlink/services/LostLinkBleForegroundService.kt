package com.silentnet.lostlink.services

import android.app.*
import com.silentnet.lostlink.beacon.LostLinkBeaconPacket
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.silentnet.lostlink.LostLinkV2Manager
import kotlinx.coroutines.*

/**
 * LostLinkBleForegroundService
 * 
 * Responsibility:
 * - Persistent background BLE advertising.
 * - Manage LostLink V2 subsystem lifecycle.
 */
class LostLinkBleForegroundService : Service() {
    
    private val TAG = "LostLinkBleService".also { Log.e("SERVICE_PROOF", "SERVICE_PROOF_1") }
    private val CHANNEL_ID = "LostLinkBleChannel"
    private val NOTIFICATION_ID = 1002
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    init {
        Log.e("SERVICE_PROOF", "SERVICE_PROOF_2")
        Log.e("LOSTLINK_SERVICE", "SERVICE_INIT_BLOCK")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        Log.e("SERVICE_PROOF", "SERVICE_PROOF_3")
        try {
            Log.e("LOSTLINK_SERVICE", "SERVICE_ON_CREATE_ENTER")
            super.onCreate()
            Log.e("SERVICE_TRACE", "SERVICE_TRACE_5")
            Log.d("LostLinkService", "SERVICE_CREATED")
            createNotificationChannel()
        } catch (t: Throwable) {
            Log.e("SERVICE_PROOF", "ONCREATE_CRASH", t)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.e("SERVICE_PROOF", "SERVICE_PROOF_4")
        try {
            Log.e("LOSTLINK_SERVICE", "SERVICE_ON_START_COMMAND_ENTER")
            
            // IMMEDIATE PROMOTION TO FOREGROUND (Fix for Suspect 2: Late startForeground)
            Log.e("LOSTLINK_SERVICE", "TRACE_FOREGROUND_BEFORE")
            Log.e("LOSTLINK_SERVICE", "START_FOREGROUND_CALL_BEFORE")
            startForeground(NOTIFICATION_ID, createNotification())
            Log.e("LOSTLINK_SERVICE", "START_FOREGROUND_CALL_AFTER")
            Log.e("LOSTLINK_SERVICE", "TRACE_FOREGROUND_AFTER")

            Log.e("SERVICE_TRACE", "SERVICE_TRACE_6")
            Log.d("LostLinkService", "SERVICE_STARTED")
            Log.i(TAG, "LostLink BLE Service starting...")
            
            val manager = LostLinkV2Manager.getInstance(this)
            
            // Ensure permissions are granted before starting BLE
            if (manager.blePermissionManager.hasPermissions()) {
                Log.e("LOSTLINK_SERVICE", "SERVICE_STARTING_HARDWARE")
                
                Log.e("LOSTLINK_SERVICE", "TRACE_ROTATE_BEFORE")
                val beacon = manager.broadcaster.rotate(
                    0,
                    15 * 60 * 1000
                )
                Log.e("LOSTLINK_SERVICE", "TRACE_ROTATE_AFTER")

                val initialPacket = LostLinkBeaconPacket(
                    beaconId = beacon.beaconId,
                    rotationVersion = beacon.rotationVersion,
                    createdAt = beacon.createdAt,
                    expiresAt = beacon.expiresAt
                )

                try {
                    Log.e("LOSTLINK_SERVICE", "TRACE_ADVERTISER_BEFORE")
                    Log.e("LOSTLINK_SERVICE", "ADVERTISER_BEFORE")
                    Log.d("LostLinkService", "ADVERTISER_START")
                    manager.advertiser.start(initialPacket)
                    Log.e("LOSTLINK_SERVICE", "ADVERTISER_AFTER")
                    Log.e("LOSTLINK_SERVICE", "TRACE_ADVERTISER_AFTER")
                } catch (t: Throwable) {
                    Log.e("LOSTLINK_SERVICE", "ADVERTISER_EXCEPTION", t)
                }

                try {
                    Log.e("LOSTLINK_SERVICE", "TRACE_SCANNER_BEFORE")
                    Log.e("LOSTLINK_SERVICE", "SCANNER_BEFORE")
                    Log.d("LostLinkService", "SCANNER_START")
                    manager.scanner.start()
                    Log.e("LOSTLINK_SERVICE", "SCANNER_AFTER")
                    Log.e("LOSTLINK_SERVICE", "TRACE_SCANNER_AFTER")
                } catch (t: Throwable) {
                    Log.e("LOSTLINK_SERVICE", "SCANNER_EXCEPTION", t)
                }

                try {
                    Log.e("LOSTLINK_SERVICE", "TRACE_SCHEDULER_BEFORE")
                    Log.e("LOSTLINK_SERVICE", "SCHEDULER_BEFORE")
                    manager.rotationScheduler.start()
                    Log.e("LOSTLINK_SERVICE", "SCHEDULER_AFTER")
                    Log.e("LOSTLINK_SERVICE", "TRACE_SCHEDULER_AFTER")

                    // Ownership Heartbeat Loop
                    scope.launch {
                        while (isActive) {
                            manager.broadcaster.getActiveBeacon()?.let { beacon ->
                                Log.e("LOSTLINK_OWNERSHIP", "OWN_ID_HEARTBEAT=${beacon.beaconId}")
                            }
                            delay(30000)
                        }
                    }
                } catch (t: Throwable) {
                    Log.e("LOSTLINK_SERVICE", "SCHEDULER_EXCEPTION", t)
                }

            } else {
                Log.e(TAG, "Missing BLE permissions, service running in restricted mode")
            }

        } catch (t: Throwable) {
            Log.e("SERVICE_PROOF", "ONSTART_CRASH", t)
        }
        
        return START_STICKY
    }

    override fun onDestroy() {
        Log.e("LOSTLINK_SERVICE", "SERVICE_ON_DESTROY_ENTER")
        Log.e("LOSTLINK_DESTROY", "SERVICE_DESTROY_STACK", Throwable())
        scope.cancel()
        super.onDestroy()
        Log.i(TAG, "LostLink BLE Service stopping...")
        val manager = LostLinkV2Manager.getInstance(this)
        manager.rotationScheduler.stop()
        manager.advertiser.stop()
        manager.scanner.stop()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        Log.e("LOSTLINK_SERVICE", "SERVICE_ON_TASK_REMOVED")
        super.onTaskRemoved(rootIntent)
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("LostLink BLE Active")
            .setContentText("Broadcasting recovery beacon...")
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "LostLink BLE Service Channel",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }
}
