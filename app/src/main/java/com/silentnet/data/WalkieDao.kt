package com.silentnet.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface WalkieDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChannel(channel: WalkieChannelEntity)

    @Query("SELECT * FROM walkie_channels ORDER BY createdAt DESC")
    fun observeAllChannels(): Flow<List<WalkieChannelEntity>>

    @Query("SELECT * FROM walkie_channels WHERE channelId = :channelId LIMIT 1")
    suspend fun findChannelById(channelId: String): WalkieChannelEntity?

    @Query("UPDATE walkie_channels SET isJoined = :isJoined WHERE channelId = :channelId")
    suspend fun updateJoinStatus(channelId: String, isJoined: Boolean)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSegment(segment: WalkieSegmentEntity)

    @Query("SELECT * FROM walkie_segments WHERE channelId = :channelId ORDER BY timestamp ASC")
    fun observeSegmentsForChannel(channelId: String): Flow<List<WalkieSegmentEntity>>

    @Query("SELECT * FROM walkie_segments WHERE segmentId = :segmentId LIMIT 1")
    suspend fun findSegmentById(segmentId: String): WalkieSegmentEntity?

    @Query("DELETE FROM walkie_segments WHERE timestamp < :cutoff")
    suspend fun cleanupOldSegments(cutoff: Long)
}
