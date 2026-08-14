package com.loomytrip.mobile.data.repository

import com.loomytrip.mobile.data.network.UserProfileDto
import com.loomytrip.mobile.data.network.userApi

object ProfileRepository {
    suspend fun getMyProfile(): UserProfileDto = userApi.getMyProfile()
}
