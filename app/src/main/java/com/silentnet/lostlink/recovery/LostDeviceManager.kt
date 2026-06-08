package com.silentnet.lostlink.recovery

import com.silentnet.lostlink.data.LostLinkRecoveryEntity
import com.silentnet.lostlink.repository.LostLinkRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class LostDeviceManager(
    private val repository: LostLinkRepository,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {
    fun markDeviceLost(deviceId: String) {
        scope.launch {
            val existing = repository.getRecoveryState(deviceId)
            if (existing != null) {
                repository.updateLostStatus(deviceId, true)
            } else {
                repository.saveRecoveryState(
                    LostLinkRecoveryEntity(
                        deviceId = deviceId,
                        isLost = true,
                        recoveryModeEnabled = true,
                        lastKnownSightingId = null,
                        recoveryTimelineJson = "[]",
                        updatedAt = System.currentTimeMillis()
                    )
                )
            }
        }
    }

    fun markDeviceRecovered(deviceId: String) {
        scope.launch {
            repository.updateLostStatus(deviceId, false)
        }
    }

    fun enableRecoveryMode(deviceId: String) {
        scope.launch {
            val state = repository.getRecoveryState(deviceId)
            if (state != null) {
                repository.saveRecoveryState(state.copy(recoveryModeEnabled = true, updatedAt = System.currentTimeMillis()))
            }
        }
    }

    suspend fun getLastKnownSighting(deviceId: String) = repository.getSightingsForDevice(deviceId)
}
