package com.silentnet.lostlink.beacon

import android.util.Log
import com.silentnet.lostlink.data.LostLinkBeaconEntity
import com.silentnet.lostlink.repository.LostLinkRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.*

class LostLinkBeaconManager(
    private val repository: LostLinkRepository,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {
    private var currentBeaconId: String? = null
    private var isLowBatteryMode = false
    private var isPrivacyMode = false
    private var isStealthMode = false

    fun startBeaconRotation(ownerId: String) {
        scope.launch {
            rotateBeacon(ownerId)
        }
    }

    private suspend fun rotateBeacon(ownerId: String) {
        val newBeaconId = LostLinkBeaconCodec.toCompactId(UUID.randomUUID())
        currentBeaconId = newBeaconId
        
        val beacon = LostLinkBeaconEntity(
            beaconId = newBeaconId,
            ownerId = ownerId,
            rotatedAt = System.currentTimeMillis(),
            isPrivate = isPrivacyMode,
            isStealth = isStealthMode,
            intervalMs = if (isLowBatteryMode) 600000 else 60000,
            expiration = System.currentTimeMillis() + 3600000
        )
        
        repository.saveBeacon(beacon)
        Log.d("LostLinkBeacon", "Beacon rotated: $newBeaconId")
    }

    fun setLowBatteryMode(enabled: Boolean) {
        isLowBatteryMode = enabled
    }

    fun setPrivacyMode(enabled: Boolean) {
        isPrivacyMode = enabled
    }

    fun setStealthMode(enabled: Boolean) {
        isStealthMode = enabled
    }

    fun getCurrentBeaconId(): String? = currentBeaconId
}
