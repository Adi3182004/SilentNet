package com.silentnet.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: MessageEntity): Long

    @Query("SELECT * FROM messages WHERE contactId = :contactId AND remoteId = :remoteId LIMIT 1")
    suspend fun findByRemoteId(contactId: Long, remoteId: Long): MessageEntity?

    @Query("UPDATE messages SET deliveryStatus = :status WHERE id = :id")
    suspend fun updateDeliveryStatus(id: Long, status: Int)

    @Query("UPDATE messages SET attachmentPath = :path WHERE id = :id")
    suspend fun updateAttachmentPath(id: Long, path: String)

    @Query("SELECT * FROM messages WHERE contactId = :contactId ORDER BY timestamp ASC")
    fun observeMessages(contactId: Long): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE deliveryStatus = 0 AND isOutgoing = 1 ORDER BY timestamp ASC")
    suspend fun getPendingMessages(): List<MessageEntity>

    @Query("UPDATE messages SET deliveryStatus = 0 WHERE deliveryStatus = 1 AND isOutgoing = 1 AND timestamp < :timeoutThreshold")
    suspend fun resetTransmittingMessages(timeoutThreshold: Long)

    @Query(
        """
        SELECT m.* FROM messages m
        INNER JOIN contacts c ON m.contactId = c.id
        WHERE c.ownerUsername = :ownerUsername AND m.deliveryStatus = 0 AND m.isOutgoing = 1
        ORDER BY m.timestamp ASC
        """
    )
    fun observePendingMessages(ownerUsername: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE contactId = :contactId AND deliveryStatus = 0 AND isOutgoing = 1 ORDER BY timestamp ASC")
    suspend fun getPendingMessagesForContact(contactId: Long): List<MessageEntity>

    @Query(
        """
        SELECT m.* FROM messages m
        INNER JOIN contacts c ON m.contactId = c.id
        WHERE c.ownerUsername = :ownerUsername
        ORDER BY m.timestamp DESC
        """
    )
    fun observeAllMessages(ownerUsername: String): Flow<List<MessageEntity>>

    @Query(
        """
        SELECT m.* FROM messages m
        INNER JOIN contacts c ON m.contactId = c.id
        WHERE c.ownerUsername = :ownerUsername AND m.attachmentPath IS NOT NULL
        ORDER BY m.timestamp DESC
        """
    )
    fun observeAttachedMessages(ownerUsername: String): Flow<List<MessageEntity>>

    @Query(
        """
        SELECT COUNT(*) FROM messages m
        INNER JOIN contacts c ON m.contactId = c.id
        WHERE c.ownerUsername = :ownerUsername
        """
    )
    suspend fun countForOwner(ownerUsername: String): Int

    @Query("UPDATE messages SET isAcknowledged = 1 WHERE id = :id")
    suspend fun markAsAcknowledged(id: Long)

    @Query("UPDATE messages SET isDeleted = 1 WHERE id = :id")
    suspend fun markAsDeleted(id: Long)

    @Query("UPDATE messages SET isConsumed = 1 WHERE id = :id")
    suspend fun markAsConsumed(id: Long)

    @Query("SELECT * FROM messages WHERE id = :id")
    suspend fun getById(id: Long): MessageEntity?

    @Query("DELETE FROM messages WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM messages WHERE contactId = :contactId")
    suspend fun deleteByContactId(contactId: Long)

    @Query("DELETE FROM messages WHERE groupId = :groupId")
    suspend fun deleteByGroupId(groupId: String)

    @Query("DELETE FROM messages WHERE contactId IN (SELECT id FROM contacts WHERE ownerUsername = :ownerUsername)")
    suspend fun deleteForOwner(ownerUsername: String)

    @Query("SELECT COUNT(*) FROM messages WHERE deliveryStatus = 0 AND isOutgoing = 1")
    suspend fun getPendingCount(): Int

    @Query("SELECT COUNT(*) FROM messages WHERE isEmergency = 1")
    suspend fun getEmergencyCount(): Int

    @Query("SELECT COUNT(*) FROM messages WHERE isOutgoing = 1")
    suspend fun getSentCount(): Int

    @Query("SELECT COUNT(*) FROM messages WHERE deliveryStatus >= 2 AND isOutgoing = 1")
    suspend fun getDeliveredCount(): Int

    @Query("SELECT COUNT(*) FROM messages WHERE deliveryStatus = -1 AND isOutgoing = 1")
    suspend fun getFailedCount(): Int

    @Query("SELECT AVG(timestamp) FROM messages WHERE isOutgoing = 1")
    suspend fun getAverageDeliveryTime(): Long

    @Query("SELECT * FROM messages WHERE body LIKE '%' || :query || '%' AND isDeleted = 0")
    suspend fun searchMessages(query: String): List<MessageEntity>
}
