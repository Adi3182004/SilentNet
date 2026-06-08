package com.silentnet.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(user: UserEntity): Long

    @Query("SELECT * FROM users WHERE username = :username LIMIT 1")
    suspend fun findByUsername(username: String): UserEntity?

    @Query("SELECT * FROM users WHERE username LIKE '%' || :query || '%' OR fullName LIKE '%' || :query || '%' OR nickname LIKE '%' || :query || '%'")
    suspend fun searchUsers(query: String): List<UserEntity>

    @Query("SELECT COUNT(*) FROM users")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM users")
    fun countFlow(): Flow<Int>

    @Query("SELECT * FROM users ORDER BY fullName COLLATE NOCASE ASC")
    suspend fun allUsers(): List<UserEntity>
}
