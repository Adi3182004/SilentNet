package com.silentnet.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ContactDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(contact: ContactEntity): Long

    @Query("SELECT * FROM contacts WHERE ownerUsername = :ownerUsername AND contactUsername = :contactUsername LIMIT 1")
    suspend fun findByContactUsername(ownerUsername: String, contactUsername: String): ContactEntity?

    @Query("SELECT * FROM contacts WHERE ownerUsername = :ownerUsername ORDER BY isPinned DESC, alias COLLATE NOCASE ASC")
    fun observeContacts(ownerUsername: String): Flow<List<ContactEntity>>

    @Query("SELECT * FROM contacts WHERE ownerUsername = :ownerUsername ORDER BY isPinned DESC, alias COLLATE NOCASE ASC")
    suspend fun contacts(ownerUsername: String): List<ContactEntity>

    @Query("UPDATE contacts SET isPinned = :isPinned WHERE id = :contactId")
    suspend fun updatePinnedStatus(contactId: Long, isPinned: Boolean)

    @Query("SELECT COUNT(*) FROM contacts WHERE ownerUsername = :ownerUsername")
    suspend fun count(ownerUsername: String): Int

    @Query("SELECT * FROM contacts WHERE id = :contactId LIMIT 1")
    suspend fun findById(contactId: Long): ContactEntity?

    @Query("DELETE FROM contacts WHERE ownerUsername = :ownerUsername")
    suspend fun deleteForOwner(ownerUsername: String)
}
