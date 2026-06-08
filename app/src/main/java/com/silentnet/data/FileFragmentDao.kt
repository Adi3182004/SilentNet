package com.silentnet.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface FileFragmentDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFragment(fragment: FileFragmentEntity)

    @Query("SELECT * FROM file_fragments WHERE fileId = :fileId AND senderNodeId = :senderId ORDER BY fragmentIndex ASC")
    suspend fun getFragmentsForFile(fileId: String, senderId: String): List<FileFragmentEntity>

    @Query("SELECT COUNT(*) FROM file_fragments WHERE fileId = :fileId AND senderNodeId = :senderId")
    suspend fun getFragmentCount(fileId: String, senderId: String): Int

    @Query("SELECT * FROM file_fragments WHERE fileId = :fileId AND senderNodeId = :senderId ORDER BY fragmentIndex ASC")
    fun observeFragments(fileId: String, senderId: String): Flow<List<FileFragmentEntity>>

    @Query("DELETE FROM file_fragments WHERE fileId = :fileId")
    suspend fun deleteFragmentsForFile(fileId: String)

    @Query("DELETE FROM file_fragments WHERE timestamp < :cutoff")
    suspend fun cleanupOldFragments(cutoff: Long)

    @Query("SELECT totalFragments FROM file_fragments WHERE fileId = :fileId LIMIT 1")
    suspend fun getTotalFragments(fileId: String): Int

    @Query("SELECT fragmentIndex FROM file_fragments WHERE fileId = :fileId")
    suspend fun getReceivedFragmentIndices(fileId: String): List<Int>

    @Query("SELECT * FROM file_fragments WHERE fileId = :fileId AND fragmentIndex = :index LIMIT 1")
    suspend fun getFragment(fileId: String, index: Int): FileFragmentEntity?
}
