package com.silentnet.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "groups",
    indices = [Index(value = ["groupId"], unique = true)]
)
data class GroupEntity(
    @PrimaryKey val groupId: String,
    val name: String,
    val description: String?,
    val creatorNodeId: String,
    val type: Int, // 0: Private, 1: Emergency, 2: Broadcast
    val currentKeyId: String?,
    val isPinned: Boolean = false,
    val isJoined: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "group_members",
    primaryKeys = ["groupId", "nodeId"]
)
data class GroupMemberEntity(
    val groupId: String,
    val nodeId: String,
    val alias: String,
    val role: Int = 0, // 0: Member, 1: Admin
    val joinTime: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "group_keys",
    primaryKeys = ["groupId", "keyId"]
)
data class GroupKeyEntity(
    val groupId: String,
    val keyId: String,
    val encryptedKey: String, // The symmetric key encrypted for the local user via identity key
    val createdAt: Long = System.currentTimeMillis()
)
