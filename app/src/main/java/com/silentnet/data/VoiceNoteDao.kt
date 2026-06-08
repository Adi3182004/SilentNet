package com.silentnet.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface VoiceNoteDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(voiceNote: VoiceNoteEntity): Long

    @Update
    suspend fun update(voiceNote: VoiceNoteEntity)

    @Query("SELECT * FROM voice_notes WHERE voiceNoteId = :voiceNoteId LIMIT 1")
    suspend fun findById(voiceNoteId: String): VoiceNoteEntity?

    @Query("SELECT * FROM voice_notes ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<VoiceNoteEntity>>

    @Query("SELECT * FROM voice_notes WHERE recipient = :nodeId OR sender = :nodeId OR groupId IN (SELECT groupNodeId FROM recovery_groups WHERE isJoined = 1) ORDER BY timestamp DESC")
    fun observeForNode(nodeId: String): Flow<List<VoiceNoteEntity>>

    @Query("UPDATE voice_notes SET deliveryState = :state WHERE voiceNoteId = :voiceNoteId")
    suspend fun updateDeliveryState(voiceNoteId: String, state: Int)

    @Query("UPDATE voice_notes SET receivedFragments = receivedFragments + 1 WHERE voiceNoteId = :voiceNoteId")
    suspend fun incrementFragments(voiceNoteId: String)

    @Query("DELETE FROM voice_notes WHERE voiceNoteId = :voiceNoteId")
    suspend fun delete(voiceNoteId: String)
}
