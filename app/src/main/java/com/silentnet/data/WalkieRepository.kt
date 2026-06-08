package com.silentnet.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class WalkieRepository(private val walkieDao: WalkieDao) {

    fun observeAllChannels(): Flow<List<WalkieChannelEntity>> = walkieDao.observeAllChannels()

    suspend fun findChannelById(channelId: String): WalkieChannelEntity? = withContext(Dispatchers.IO) {
        walkieDao.findChannelById(channelId)
    }

    suspend fun insertChannel(channel: WalkieChannelEntity) = withContext(Dispatchers.IO) {
        walkieDao.insertChannel(channel)
    }

    suspend fun updateJoinStatus(channelId: String, isJoined: Boolean) = withContext(Dispatchers.IO) {
        walkieDao.updateJoinStatus(channelId, isJoined)
    }

    suspend fun insertSegment(segment: WalkieSegmentEntity) = withContext(Dispatchers.IO) {
        walkieDao.insertSegment(segment)
    }

    fun observeSegmentsForChannel(channelId: String): Flow<List<WalkieSegmentEntity>> = walkieDao.observeSegmentsForChannel(channelId)

    suspend fun cleanupOldSegments(cutoff: Long) = withContext(Dispatchers.IO) {
        walkieDao.cleanupOldSegments(cutoff)
    }
}
