package com.silentnet.lostlink.platform

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * BlePermissionManager
 * 
 * Responsibility:
 * - Check and request BLE-related permissions.
 * - Handle Android 12+ (API 31) permission model changes.
 */
class BlePermissionManager(private val context: Context) {

    /**
     * Foreground permissions for BLE Advertising and Scanning.
     */
    val foregroundPermissions: Array<String>
        get() {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                arrayOf(
                    Manifest.permission.BLUETOOTH_ADVERTISE,
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.ACCESS_FINE_LOCATION
                )
            } else {
                arrayOf(
                    Manifest.permission.BLUETOOTH,
                    Manifest.permission.BLUETOOTH_ADMIN,
                    Manifest.permission.ACCESS_FINE_LOCATION
                )
            }
        }

    /**
     * Background permission for persistent tracking.
     */
    val backgroundPermissions: Array<String>
        get() = arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION)

    /**
     * Checks if all required permissions are granted.
     */
    fun hasPermissions(): Boolean {
        return foregroundPermissions.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * Request permissions from an Activity.
     */
    fun requestPermissions(activity: Activity, requestCode: Int) {
        ActivityCompat.requestPermissions(activity, foregroundPermissions, requestCode)
    }
}
