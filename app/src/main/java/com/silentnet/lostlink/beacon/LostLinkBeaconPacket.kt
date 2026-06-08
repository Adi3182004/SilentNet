package com.silentnet.lostlink.beacon

/**
 * LostLinkBeaconPacket
 * 
 * Data structure for the actual data transmitted over the air.
 */
data class LostLinkBeaconPacket(
    val beaconId: String,
    val rotationVersion: Int,
    val createdAt: Long,
    val expiresAt: Long,
    val protocolVersion: Int = 2
)
