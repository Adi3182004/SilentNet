package com.silentnet.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "file_fragments",
    indices = [Index(value = ["fileId"]), Index(value = ["targetId"])]
)
data class FileFragmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fileId: String,
    val fragmentIndex: Int,
    val totalFragments: Int,
    val data: ByteArray,
    val checksum: String,
    val targetId: String, // Recipient or GroupId
    val isGroup: Boolean = false,
    val senderNodeId: String? = null,
    val timestamp: Long = System.currentTimeMillis()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as FileFragmentEntity
        if (id != other.id) return false
        return true
    }

    override fun hashCode(): Int = id.hashCode()
}
