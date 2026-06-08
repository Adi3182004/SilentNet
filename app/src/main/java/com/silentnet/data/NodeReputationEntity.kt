package com.silentnet.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "node_reputation")
data class NodeReputationEntity(
    @PrimaryKey val nodeId: String,
    val successCount: Int = 0,
    val failureCount: Int = 0,
    val packetsRelayed: Int = 0,
    val packetsDropped: Int = 0,
    val lastSeen: Long = System.currentTimeMillis(),
    val stabilityScore: Double = 1.0, // 0.0 to 1.0
    val contributionScore: Double = 0.0 // Based on relay activity
) {
    fun getReputationMultiplier(): Double {
        val total = successCount + failureCount
        if (total == 0) return 0.5
        val reliability = successCount.toDouble() / total.toDouble()
        return (reliability * 0.7 + stabilityScore * 0.3)
    }
}
