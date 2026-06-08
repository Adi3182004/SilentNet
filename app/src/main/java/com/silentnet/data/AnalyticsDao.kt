package com.silentnet.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface AnalyticsDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAnalytics(analytics: MeshAnalyticsEntity)

    @Query("SELECT * FROM mesh_analytics WHERE date = :date")
    suspend fun getAnalyticsForDate(date: String): MeshAnalyticsEntity?

    @Query("SELECT * FROM mesh_analytics ORDER BY date DESC LIMIT 30")
    suspend fun getRecentAnalytics(): List<MeshAnalyticsEntity>

    @Insert
    suspend fun insertEvent(event: NetworkEventEntity)

    @Query("SELECT * FROM network_events ORDER BY timestamp DESC LIMIT 100")
    fun getRecentEventsFlow(): kotlinx.coroutines.flow.Flow<List<NetworkEventEntity>>

    @Query("DELETE FROM network_events WHERE timestamp < :threshold")
    suspend fun cleanupEvents(threshold: Long)

    @Query("UPDATE mesh_analytics SET packetsRelayed = packetsRelayed + 1 WHERE date = :date")
    suspend fun incrementRelayCount(date: String)

    @Query("UPDATE mesh_analytics SET emergencyRelays = emergencyRelays + 1 WHERE date = :date")
    suspend fun incrementEmergencyRelayCount(date: String)

    @Query("UPDATE mesh_analytics SET storeForwardDeliveries = storeForwardDeliveries + 1 WHERE date = :date")
    suspend fun incrementStoreForwardCount(date: String)

    @Query("UPDATE mesh_analytics SET groupRelays = groupRelays + 1 WHERE date = :date")
    suspend fun incrementGroupRelayCount(date: String)

    @Query("UPDATE mesh_analytics SET recoveryRelays = recoveryRelays + 1 WHERE date = :date")
    suspend fun incrementRecoveryRelayCount(date: String)
}
