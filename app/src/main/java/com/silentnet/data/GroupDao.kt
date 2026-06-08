package com.silentnet.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface GroupDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGroup(group: GroupEntity)

    @Query("SELECT * FROM groups WHERE isJoined = 1 ORDER BY isPinned DESC, createdAt DESC")
    fun observeAllGroups(): Flow<List<GroupEntity>>

    @Query("UPDATE groups SET isPinned = :isPinned WHERE groupId = :groupId")
    suspend fun updatePinnedStatus(groupId: String, isPinned: Boolean)

    @Query("SELECT * FROM groups WHERE groupId = :groupId LIMIT 1")
    suspend fun findById(groupId: String): GroupEntity?

    @Query("SELECT * FROM groups WHERE groupId = :groupId LIMIT 1")
    fun observeById(groupId: String): Flow<GroupEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMember(member: GroupMemberEntity)

    @Query("SELECT * FROM group_members WHERE groupId = :groupId")
    suspend fun getMembers(groupId: String): List<GroupMemberEntity>

    @Query("SELECT * FROM group_members WHERE groupId = :groupId")
    fun observeMembers(groupId: String): Flow<List<GroupMemberEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertKey(key: GroupKeyEntity)

    @Query("SELECT * FROM group_keys WHERE groupId = :groupId AND keyId = :keyId LIMIT 1")
    suspend fun findKey(groupId: String, keyId: String): GroupKeyEntity?

    @Query("SELECT * FROM messages WHERE groupId = :groupId ORDER BY timestamp ASC")
    fun observeGroupMessages(groupId: String): Flow<List<MessageEntity>>

    @Query("UPDATE groups SET isJoined = :joined WHERE groupId = :groupId")
    suspend fun updateJoinStatus(groupId: String, joined: Boolean)

    @Query("UPDATE groups SET currentKeyId = :keyId WHERE groupId = :groupId")
    suspend fun updateCurrentKey(groupId: String, keyId: String)

    @Query("SELECT COUNT(*) FROM groups")
    suspend fun getGroupCount(): Int

    @Query("SELECT COUNT(*) FROM groups WHERE isJoined = 1")
    suspend fun getJoinedGroupCount(): Int

    @Query("DELETE FROM group_members WHERE groupId = :groupId AND nodeId = :nodeId")
    suspend fun deleteMember(groupId: String, nodeId: String)

    @Query("DELETE FROM group_members WHERE groupId = :groupId")
    suspend fun deleteAllMembers(groupId: String)

    @Query("DELETE FROM group_keys WHERE groupId = :groupId")
    suspend fun deleteGroupKeys(groupId: String)

    @Query("DELETE FROM groups WHERE groupId = :groupId")
    suspend fun deleteGroup(groupId: String)
}
