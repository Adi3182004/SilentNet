package com.silentnet.lostlink.beacon

import kotlinx.coroutines.*
import java.util.concurrent.TimeUnit

/**
 * BeaconRotationScheduler
 * 
 * Responsibility:
 * - Rotate beacon every configurable interval.
 * - Must survive app lifecycle (managed by LostLinkForegroundService).
 */
class BeaconRotationScheduler(
    private val broadcaster: LostLinkBeaconBroadcaster,
    private val intervalMs: Long = TimeUnit.MINUTES.toMillis(15),
    private val advertiser: LostLinkBeaconAdvertiser? = null,
    private val stats: LostLinkBeaconStats? = null
) {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var rotationJob: Job? = null
    private var version = 0

    fun start() {
        if (rotationJob?.isActive == true) return
        
        rotationJob = scope.launch {
            // Wait for initial advertisement from service startup to settle
            delay(intervalMs)
            while (isActive) {
                version++
                val payload = broadcaster.rotate(version, intervalMs)
                
                // Update stats
                stats?.onBeaconRotated(payload.beaconId)
                
                // Update advertiser
                advertiser?.update(
                    LostLinkBeaconPacket(
                        beaconId = payload.beaconId,
                        rotationVersion = payload.rotationVersion,
                        createdAt = payload.createdAt,
                        expiresAt = payload.expiresAt
                    )
                )
                
                delay(intervalMs)
            }
        }
    }

    fun stop() {
        rotationJob?.cancel()
    }
}
