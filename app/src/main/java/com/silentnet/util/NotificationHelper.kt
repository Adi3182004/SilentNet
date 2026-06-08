package com.silentnet.util

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import com.silentnet.R

object NotificationHelper {
    private const val CHANNEL_ID = "transport_service_channel"
    private const val CHANNEL_NAME = "Transport Service"
    private const val NOTIFICATION_ID = 1

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps SilentNet mesh active in background"
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    fun getForegroundNotification(context: Context): Notification {
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("SilentNet Mesh Active")
            .setContentText("Maintaining offline connections...")
            .setSmallIcon(com.silentnet.R.mipmap.ic_launcher)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    fun getNotificationId() = NOTIFICATION_ID
}
