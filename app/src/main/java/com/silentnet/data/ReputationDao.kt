package com.silentnet.data

import androidx.room.*

@Dao
interface ReputationDao {
    @Query("SELECT * FROM node_reputation WHERE nodeId = :nodeId")
    suspend fun getReputation(nodeId: String): NodeReputationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReputation(reputation: NodeReputationEntity)

    @Query("UPDATE node_reputation SET successCount = successCount + 1, lastSeen = :now WHERE nodeId = :nodeId")
    suspend fun recordSuccess(nodeId: String, now: Long = System.currentTimeMillis())

    @Query("UPDATE node_reputation SET failureCount = failureCount + 1, lastSeen = :now WHERE nodeId = :nodeId")
    suspend fun recordFailure(nodeId: String, now: Long = System.currentTimeMillis())

    @Query("UPDATE node_reputation SET packetsRelayed = packetsRelayed + 1 WHERE nodeId = :nodeId")
    suspend fun recordRelay(nodeId: String)
}
