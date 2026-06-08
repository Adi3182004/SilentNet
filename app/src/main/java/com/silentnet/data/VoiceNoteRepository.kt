package com.silentnet.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class VoiceNoteRepository(private val voiceNoteDao: VoiceNoteDao) {

    fun observeVoiceNotes(nodeId: String): Flow<List<VoiceNoteEntity>> = voiceNoteDao.observeForNode(nodeId)

    suspend fun insert(voiceNote: VoiceNoteEntity): Long = withContext(Dispatchers.IO) {
        voiceNoteDao.insert(voiceNote)
    }

    suspend fun update(voiceNote: VoiceNoteEntity) = withContext(Dispatchers.IO) {
        voiceNoteDao.update(voiceNote)
    }

    suspend fun findById(voiceNoteId: String): VoiceNoteEntity? = withContext(Dispatchers.IO) {
        voiceNoteDao.findById(voiceNoteId)
    }

    suspend fun updateDeliveryState(voiceNoteId: String, state: Int) = withContext(Dispatchers.IO) {
        voiceNoteDao.updateDeliveryState(voiceNoteId, state)
    }

    suspend fun incrementFragments(voiceNoteId: String) = withContext(Dispatchers.IO) {
        voiceNoteDao.incrementFragments(voiceNoteId)
    }
}
