package com.silentnet.ui.main

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.silentnet.SilentNetApplication
import com.silentnet.data.MessageEntity
import com.silentnet.ui.auth.AuthActivity
import com.silentnet.ui.theme.SilentNetTheme

class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            startTransport()
        } else {
            Toast.makeText(this, "Permissions required for offline messaging", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as SilentNetApplication

        val session = app.graph.sessionManager
        if (!session.hasSession()) {
            startActivity(Intent(this, AuthActivity::class.java))
            finish()
            return
        }

        checkAndRequestPermissions()
        
        val transport = app.graph.transportManager
        transport.setEmergencyAlertManager(app.graph.emergencyAlertManager)

        setContent {
            SilentNetTheme {
                var emergencyToShow by remember { mutableStateOf<MessageEntity?>(null) }

                LaunchedEffect(Unit) {
                    transport.emergencyEvents.collect {
                        emergencyToShow = it
                    }
                }

                if (emergencyToShow != null) {
                    EmergencyPopup(
                        message = emergencyToShow!!,
                        onAcknowledge = {
                            transport.acknowledgeEmergency(emergencyToShow!!.id)
                            emergencyToShow = null
                        },
                        onDismiss = {
                            emergencyToShow = null
                        }
                    )
                }

                MainScreen(
                    graph = app.graph,
                    onLogout = {
                        stopService(Intent(this, com.silentnet.transport.TransportService::class.java))
                        app.graph.sessionManager.clear()
                        startActivity(Intent(this, AuthActivity::class.java))
                        finish()
                    }
                )
            }
        }
    }

    private fun checkAndRequestPermissions() {
        val permissions = mutableListOf<String>()
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.NEARBY_WIFI_DEVICES)
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            permissions.add(Manifest.permission.BLUETOOTH_ADVERTISE)
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            permissions.add(Manifest.permission.BLUETOOTH_ADVERTISE)
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        }
        
        permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        permissions.add(Manifest.permission.RECORD_AUDIO)
        permissions.add(Manifest.permission.CAMERA)

        val missingPermissions = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isEmpty()) {
            startTransport()
        } else {
            requestPermissionLauncher.launch(missingPermissions.toTypedArray())
        }
    }

    private fun startTransport() {
        val serviceIntent = Intent(this, com.silentnet.transport.TransportService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Removed stop() to allow background delivery
    }
}
