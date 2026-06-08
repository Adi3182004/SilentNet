package com.silentnet.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface RecoveryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPost(post: RecoveryPostEntity)

    @Query("SELECT * FROM recovery_posts ORDER BY priority DESC, timestamp DESC")
    fun observeAllPosts(): Flow<List<RecoveryPostEntity>>

    @Query("SELECT * FROM recovery_posts WHERE category = :category ORDER BY priority DESC, timestamp DESC")
    fun observePostsByCategory(category: String): Flow<List<RecoveryPostEntity>>

    @Query("SELECT * FROM recovery_posts WHERE content LIKE '%' || :query || '%' OR authorAlias LIKE '%' || :query || '%' ORDER BY priority DESC, timestamp DESC")
    fun searchPosts(query: String): Flow<List<RecoveryPostEntity>>

    @Query("DELETE FROM recovery_posts WHERE expiration < :currentTime")
    suspend fun deleteExpired(currentTime: Long)

    @Query("SELECT EXISTS(SELECT 1 FROM recovery_posts WHERE postId = :postId)")
    suspend fun postExists(postId: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGroup(group: RecoveryGroupEntity)

    @Query("SELECT * FROM recovery_groups ORDER BY createdAt DESC")
    fun observeAllGroups(): Flow<List<RecoveryGroupEntity>>

    @Query("UPDATE recovery_groups SET isJoined = :joined WHERE groupNodeId = :groupId")
    suspend fun updateJoinStatus(groupId: String, joined: Boolean)
    
    @Query("SELECT * FROM recovery_groups WHERE groupNodeId = :groupId LIMIT 1")
    suspend fun findGroupById(groupId: String): RecoveryGroupEntity?

    @Query("SELECT COUNT(*) FROM recovery_posts")
    suspend fun getPostCount(): Int

    // LostLink
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLostDevice(device: LostDeviceEntity)

    @Query("SELECT * FROM lost_devices ORDER BY createdAt DESC")
    fun observeLostDevices(): Flow<List<LostDeviceEntity>>

    @Query("SELECT * FROM lost_devices WHERE deviceId = :deviceId LIMIT 1")
    suspend fun findLostDeviceById(deviceId: String): LostDeviceEntity?

    @Query("UPDATE lost_devices SET isLost = :isLost WHERE deviceId = :deviceId")
    suspend fun updateLostStatus(deviceId: String, isLost: Boolean)

    @Query("UPDATE lost_devices SET currentAnonymousId = :anonymousId WHERE deviceId = :deviceId")
    suspend fun updateAnonymousId(deviceId: String, anonymousId: String?)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLostReport(report: LostLinkReportEntity)

    @Query("SELECT * FROM lostlink_reports WHERE deviceId = :deviceId ORDER BY timestamp DESC")
    fun observeReportsForDevice(deviceId: String): Flow<List<LostLinkReportEntity>>
    @Query("SELECT * FROM lostlink_reports WHERE deviceId = :deviceId ORDER BY timestamp DESC")
    suspend fun getLostReports(
        deviceId: String
    ): List<LostLinkReportEntity>

    @Query("SELECT * FROM lostlink_reports ORDER BY timestamp DESC")
    fun observeAllLostReports(): Flow<List<LostLinkReportEntity>>

    // Search History
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSearch(search: SearchHistoryEntity)

    @Query("SELECT * FROM search_history ORDER BY timestamp DESC")
    fun observeSearchHistory(): Flow<List<SearchHistoryEntity>>

    @Query("DELETE FROM search_history")
    suspend fun clearSearchHistory()

    // Search Results
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSearchResult(result: SearchResultEntity)

    @Query("SELECT * FROM search_results WHERE queryId = :queryId ORDER BY rankingScore DESC")
    fun observeSearchResults(queryId: String): Flow<List<SearchResultEntity>>

    @Query("DELETE FROM search_results WHERE timestamp < :cutoff")
    suspend fun cleanupOldSearchResults(cutoff: Long)
}
