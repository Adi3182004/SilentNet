package com.silentnet.auth

import com.silentnet.data.UserEntity

sealed class AuthResult {
    data class Success(val user: UserEntity) : AuthResult()
    data class Error(val message: String) : AuthResult()
}
