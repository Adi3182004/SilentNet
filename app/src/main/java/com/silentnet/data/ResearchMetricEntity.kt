package com.silentnet.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "research_metrics")
data class ResearchMetricEntity(
    @PrimaryKey val date: String, // yyyy-MM-dd
    val gossipEfficiency: Double = 0.0, // Redundant suppressed / total received
    val averageReputation: Double = 0.0,
    val congestionScore: Double = 0.0,
    val relayLoadBalance: Double = 0.0,
    val deliveryProbability: Double = 0.0,
    val packetsSuppressed: Int = 0,
    val totalGossipPackets: Int = 0
)
