package com.loomytrip.mobile.data.repository

import com.loomytrip.mobile.data.network.AuthResponseDto
import com.loomytrip.mobile.data.network.LoginRequest
import com.loomytrip.mobile.data.network.RegisterRequest
import com.loomytrip.mobile.data.network.TokenStore
import com.loomytrip.mobile.data.network.authApi

object AuthRepository {
    suspend fun login(email: String, password: String): AuthResponseDto {
        val response = authApi.login(LoginRequest(email.trim().lowercase(), password))
        TokenStore.token = response.accessToken
        return response
    }

    suspend fun register(username: String, email: String, password: String): AuthResponseDto {
        val response = authApi.register(
            RegisterRequest(username = username.ifBlank { null }, email = email.trim().lowercase(), password = password)
        )
        TokenStore.token = response.accessToken
        return response
    }
}
