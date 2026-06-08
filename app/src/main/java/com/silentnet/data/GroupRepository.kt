package com.silentnet.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.util.UUID

class GroupRepository(private val groupDao: GroupDao) {

    fun observeAllGroups(): Flow<List<GroupEntity>> = groupDao.observeAllGroups()

    fun observeGroupById(groupId: String): Flow<GroupEntity?> = groupDao.observeById(groupId)

    fun observeGroupMessages(groupId: String): Flow<List<MessageEntity>> = groupDao.observeGroupMessages(groupId)

    fun observeMembers(groupId: String): Flow<List<GroupMemberEntity>> = groupDao.observeMembers(groupId)

    suspend fun findGroupById(groupId: String): GroupEntity? = withContext(Dispatchers.IO) {
        groupDao.findById(groupId)
    }

    suspend fun getMembers(groupId: String): List<GroupMemberEntity> = withContext(Dispatchers.IO) {
        groupDao.getMembers(groupId)
    }

    suspend fun findKey(groupId: String, keyId: String): GroupKeyEntity? = withContext(Dispatchers.IO) {
        groupDao.findKey(groupId, keyId)
    }

    suspend fun insertKey(key: GroupKeyEntity) = withContext(Dispatchers.IO) {
        groupDao.insertKey(key)
    }

    suspend fun createGroup(name: String, description: String?, creatorNodeId: String, creatorAlias: String): String = withContext(Dispatchers.IO) {
        val groupId = UUID.randomUUID().toString()
        val group = GroupEntity(
            groupId = groupId,
            name = name,
            description = description,
            creatorNodeId = creatorNodeId,
            type = 0, // Private
            currentKeyId = null,
            isJoined = true
        )
        groupDao.insertGroup(group)

        // Add creator as Admin
        groupDao.insertMember(GroupMemberEntity(
            groupId = groupId,
            nodeId = creatorNodeId,
            alias = creatorAlias,
            role = 1 // Admin
        ))

        groupId
    }

    suspend fun joinGroup(groupId: String, myNodeId: String, myAlias: String) = withContext(Dispatchers.IO) {
        groupDao.updateJoinStatus(groupId, true)
        groupDao.insertMember(GroupMemberEntity(
            groupId = groupId,
            nodeId = myNodeId,
            alias = myAlias,
            role = 0 // Member
        ))
    }

    suspend fun leaveGroup(groupId: String) = withContext(Dispatchers.IO) {
        android.util.Log.d("SilentNetGroup", "Leave Requested: $groupId")
        groupDao.updateJoinStatus(groupId, false)
        groupDao.deleteAllMembers(groupId)
        android.util.Log.d("SilentNetGroup", "Membership Removed: $groupId")
        groupDao.deleteGroupKeys(groupId)
        android.util.Log.d("SilentNetGroup", "Group Key Removed: $groupId")
        android.util.Log.d("SilentNetGroup", "Group Left Successfully: $groupId")
        // To remove visibility from the groups list but persist the leave state (so we don't re-join automatically)
        // we keep the group entry with isJoined = false.
        // If the user wants to completely remove it, we could deleteGroup(groupId).
        // 'Remove group visibility' and 'Persist leave state' suggest keeping the record but not showing it.
        // The observeAllGroups Flow should probably filter for isJoined = 1.
    }

    suspend fun insertGroup(group: GroupEntity) = withContext(Dispatchers.IO) {
        groupDao.insertGroup(group)
    }

    suspend fun updatePinnedStatus(groupId: String, isPinned: Boolean) = withContext(Dispatchers.IO) {
        groupDao.updatePinnedStatus(groupId, isPinned)
    }

    suspend fun insertMember(member: GroupMemberEntity) = withContext(Dispatchers.IO) {
        groupDao.insertMember(member)
    }

    suspend fun updateJoinStatus(groupId: String, joined: Boolean) = withContext(Dispatchers.IO) {
        groupDao.updateJoinStatus(groupId, joined)
    }

    suspend fun updateCurrentKey(groupId: String, keyId: String) = withContext(Dispatchers.IO) {
        groupDao.updateCurrentKey(groupId, keyId)
    }

    suspend fun removeMember(groupId: String, nodeId: String) = withContext(Dispatchers.IO) {
        groupDao.deleteMember(groupId, nodeId)
    }
}
