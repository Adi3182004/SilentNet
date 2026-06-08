package com.silentnet.lostlink.services

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.silentnet.lostlink.LostLinkV2Manager

/**
 * LostLinkForegroundService
 * 
 * Responsibility:
 * - Start scheduler.
 * - Maintain observation manager.
 * - Keep beacon system alive.
 * - Purely internal subsystem lifecycle.
 */
class LostLinkForegroundService : Service() {
    
    private val CHANNEL_ID = "LostLinkServiceChannel"
    private val NOTIFICATION_ID = 1001

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val manager = LostLinkV2Manager.getInstance(this)
        
        // Start Beacon Rotation Scheduler
        manager.rotationScheduler.start()

        startForeground(NOTIFICATION_ID, createNotification())
        
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        val manager = LostLinkV2Manager.getInstance(this)
        manager.rotationScheduler.stop()
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("LostLink Active")
            .setContentText("Monitoring beacon sightings...")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "LostLink Service Channel",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }
}
