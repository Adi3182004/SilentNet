package com.silentnet.data

import androidx.room.*

@Dao
interface ResearchDao {
    @Query("SELECT * FROM research_metrics WHERE date = :date")
    suspend fun getMetricsForDate(date: String): ResearchMetricEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMetrics(metrics: ResearchMetricEntity)

    @Query("UPDATE research_metrics SET packetsSuppressed = packetsSuppressed + 1 WHERE date = :date")
    suspend fun incrementSuppressed(date: String)

    @Query("UPDATE research_metrics SET totalGossipPackets = totalGossipPackets + 1 WHERE date = :date")
    suspend fun incrementTotalGossip(date: String)
}
