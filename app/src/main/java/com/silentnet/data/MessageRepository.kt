package com.silentnet.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MessageRepository(
    private val messageDao: MessageDao
) {

    fun observeMessages(contactId: Long) = messageDao.observeMessages(contactId)
    fun observeAllMessages(ownerUsername: String) = messageDao.observeAllMessages(ownerUsername)
    fun observeAttachedMessages(ownerUsername: String) = messageDao.observeAttachedMessages(ownerUsername)
    fun observePendingMessages(ownerUsername: String) = messageDao.observePendingMessages(ownerUsername)

    suspend fun getPendingMessagesForContact(contactId: Long) = messageDao.getPendingMessagesForContact(contactId)
    
    suspend fun getPendingMessages() = messageDao.getPendingMessages()

    suspend fun getPendingCount() = messageDao.getPendingCount()

    suspend fun resetTransmittingMessages(timeoutMs: Long) {
        val threshold = System.currentTimeMillis() - timeoutMs
        withContext(Dispatchers.IO) {
            messageDao.resetTransmittingMessages(threshold)
        }
    }

    suspend fun findByRemoteId(contactId: Long, remoteId: Long) = messageDao.findByRemoteId(contactId, remoteId)

    suspend fun markAsAcknowledged(id: Long) = messageDao.markAsAcknowledged(id)

    suspend fun markAsDeleted(id: Long) = messageDao.markAsDeleted(id)

    suspend fun markAsConsumed(id: Long) = messageDao.markAsConsumed(id)

    suspend fun getById(id: Long) = messageDao.getById(id)

    suspend fun deleteById(id: Long) = messageDao.deleteById(id)

    suspend fun deleteByContactId(contactId: Long) = messageDao.deleteByContactId(contactId)

    suspend fun deleteByGroupId(groupId: String) = messageDao.deleteByGroupId(groupId)

    suspend fun insert(message: MessageEntity): Long {
        return withContext(Dispatchers.IO) {
            messageDao.insert(message)
        }
    }

    suspend fun updateDeliveryStatus(id: Long, status: Int) {
        withContext(Dispatchers.IO) {
            messageDao.updateDeliveryStatus(id, status)
        }
    }

    suspend fun updateAttachmentPath(id: Long, path: String) {
        withContext(Dispatchers.IO) {
            messageDao.updateAttachmentPath(id, path)
        }
    }

    suspend fun countForOwner(ownerUsername: String): Int {
        return withContext(Dispatchers.IO) {
            messageDao.countForOwner(ownerUsername)
        }
    }

    suspend fun clearOwnerData(ownerUsername: String) {
        withContext(Dispatchers.IO) {
            messageDao.deleteForOwner(ownerUsername)
        }
    }

    suspend fun searchMessages(query: String): List<MessageEntity> = withContext(Dispatchers.IO) {
        messageDao.searchMessages(query)
    }
}
