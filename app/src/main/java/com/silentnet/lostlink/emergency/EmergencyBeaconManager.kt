package com.silentnet.lostlink.emergency

import com.silentnet.lostlink.data.LostLinkEmergencyEntity
import com.silentnet.lostlink.repository.LostLinkRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.*

class EmergencyBeaconManager(
    private val repository: LostLinkRepository,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {
    fun broadcastEmergency(type: String, priority: Int) {
        scope.launch {
            val emergency = LostLinkEmergencyEntity(
                beaconId = UUID.randomUUID().toString(),
                type = type,
                priority = priority,
                ttl = 10,
                relayCount = 0,
                createdAt = System.currentTimeMillis(),
                expirationTime = System.currentTimeMillis() + 14400000 // 4 hours
            )
            repository.saveEmergency(emergency)
        }
    }
}
