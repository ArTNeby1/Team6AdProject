package com.loomytrip.mobile.data.repository

import com.loomytrip.mobile.data.network.UserNotificationDto
import com.loomytrip.mobile.data.network.notificationApi

/** Keeps notification calls separate from UI navigation and rendering. */
object NotificationRepository {
    suspend fun notifications(): List<UserNotificationDto> = notificationApi.getNotifications()

    suspend fun markRead(notificationId: Long): UserNotificationDto =
        notificationApi.markRead(notificationId)
}
