package com.silentnet.transport

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import com.silentnet.SilentNetApplication
import com.silentnet.util.NotificationHelper
import kotlinx.coroutines.*

class TransportService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var wakeLock: PowerManager.WakeLock? = null
    private val TAG = "TransportService"

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "TransportService Created")
        NotificationHelper.createNotificationChannel(this)
        startForeground(
            NotificationHelper.getNotificationId(),
            NotificationHelper.getForegroundNotification(this)
        )
        acquireWakeLock()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "TransportService started with action: ${intent?.action}")
        
        val app = application as SilentNetApplication
        val sessionManager = app.graph.sessionManager
        val transportManager = app.graph.transportManager

        if (sessionManager.hasSession()) {
            val username = sessionManager.currentUsername() ?: ""
            val fullName = sessionManager.currentFullName() ?: ""
            val nodeId = sessionManager.nodeId()
            
            // Start TransportManager if not already started
            transportManager.start(username, fullName, null, nodeId)
        } else {
            stopSelf()
        }

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "TransportService Destroyed")
        releaseWakeLock()
        serviceScope.cancel()
        
        val app = application as SilentNetApplication
        app.graph.transportManager.stop()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun acquireWakeLock() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SilentNet:TransportWakeLock")
        wakeLock?.acquire()
        Log.d(TAG, "WakeLock acquired")
    }

    private fun releaseWakeLock() {
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
            Log.d(TAG, "WakeLock released")
        }
    }
}
