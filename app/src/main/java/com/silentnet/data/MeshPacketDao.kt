package com.silentnet.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface MeshPacketDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(packet: MeshPacketEntity)

    @Query("SELECT * FROM mesh_packets WHERE targetNodeId = :targetNodeId ORDER BY timestamp ASC")
    suspend fun getPacketsForTarget(targetNodeId: String): List<MeshPacketEntity>

    @Query("SELECT * FROM mesh_packets ORDER BY timestamp ASC")
    suspend fun getAllPackets(): List<MeshPacketEntity>

    @Query("DELETE FROM mesh_packets WHERE messageRemoteId = :remoteId")
    suspend fun deleteByRemoteId(remoteId: Long)

    @Query("DELETE FROM mesh_packets WHERE packetId = :packetId")
    suspend fun deleteByPacketId(packetId: String)

    @Query("DELETE FROM mesh_packets WHERE expirationTime < :currentTime")
    suspend fun deleteExpired(currentTime: Long)

    @Query("UPDATE mesh_packets SET retryCount = retryCount + 1 WHERE packetId = :packetId")
    suspend fun incrementRetryCount(packetId: String)

    @Query("UPDATE mesh_packets SET predictionScore = :score WHERE packetId = :packetId")
    suspend fun updatePredictionScore(packetId: String, score: Double)
    
    @Query("SELECT EXISTS(SELECT 1 FROM mesh_packets WHERE packetId = :packetId)")
    suspend fun exists(packetId: String): Boolean

    @Query("SELECT COUNT(*) FROM mesh_packets")
    suspend fun getPacketCount(): Int

    @Query("SELECT * FROM mesh_packets ORDER BY timestamp DESC")
    fun getAllPacketsFlow(): kotlinx.coroutines.flow.Flow<List<MeshPacketEntity>>
}
