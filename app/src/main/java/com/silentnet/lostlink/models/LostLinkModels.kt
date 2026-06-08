package com.silentnet.lostlink.models

import org.json.JSONObject

data class BeaconPacket(
    val beaconId: String,
    val timestamp: Long,
    val data: String, // Encrypted payload
    val type: String = "BEACON"
) {
    fun toJson(): String {
        return JSONObject().apply {
            put("bid", beaconId)
            put("ts", timestamp)
            put("data", data)
            put("type", type)
        }.toString()
    }
}

data class BeaconIdentity(
    val deviceId: String,
    val publicKey: String,
    val secretKey: String? = null
)

data class BeaconObservation(
    val beaconId: String,
    val rssi: Int,
    val timestamp: Long,
    val observerNodeId: String
)

data class LostLinkDeliveryReport(
    val packetId: String,
    val status: String, // DELIVERED, EXPIRED, FORWARDED
    val relayNodeId: String,
    val timestamp: Long
)

data class LostLinkRelayStats(
    val totalStored: Int,
    val totalForwarded: Int,
    val totalExpired: Int,
    val contributionScore: Double
)
