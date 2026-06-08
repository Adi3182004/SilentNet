package com.silentnet.data

import androidx.room.*

@Dao
interface TrustedAdminDao {
    @Query("SELECT * FROM trusted_admins")
    suspend fun getAllAdmins(): List<TrustedAdminEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAdmin(admin: TrustedAdminEntity)

    @Delete
    suspend fun deleteAdmin(admin: TrustedAdminEntity)

    @Query("SELECT EXISTS(SELECT 1 FROM trusted_admins WHERE publicKeyBase64 = :publicKeyBase64)")
    suspend fun isAdminTrusted(publicKeyBase64: String): Boolean
}
