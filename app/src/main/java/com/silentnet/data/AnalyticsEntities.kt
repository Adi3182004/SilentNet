package com.silentnet.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "mesh_analytics")
data class MeshAnalyticsEntity(
    @PrimaryKey val date: String, // YYYY-MM-DD
    val messagesSent: Int = 0,
    val messagesDelivered: Int = 0,
    val messagesFailed: Int = 0,
    val averageDeliveryTime: Long = 0,
    val averageHopCount: Double = 0.0,
    val averageRouteQuality: Double = 0.0,
    val packetsRelayed: Int = 0,
    val emergencyRelays: Int = 0,
    val storeForwardDeliveries: Int = 0,
    val groupRelays: Int = 0,
    val recoveryRelays: Int = 0
)

@Entity(tableName = "network_events")
data class NetworkEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String, // Node Joined, Node Left, Route Learned, Route Updated, Route Repaired, Packet Delivered, Packet Failed, Emergency Broadcast, Recovery Broadcast, Group Event
    val details: String,
    val timestamp: Long = System.currentTimeMillis()
)
