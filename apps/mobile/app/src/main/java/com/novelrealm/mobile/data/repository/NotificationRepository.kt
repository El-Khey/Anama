package com.novelrealm.mobile.data.repository

import com.novelrealm.mobile.data.remote.ApiResult
import com.novelrealm.mobile.data.remote.api.NotificationApi
import com.novelrealm.mobile.data.remote.dto.NotificationDto
import com.novelrealm.mobile.data.remote.dto.PageDto
import com.novelrealm.mobile.data.remote.safeApiCall

// La cloche (issue #45, §3) : liste paginée, badge, marquage lu.
class NotificationRepository(private val notificationApi: NotificationApi) {

    suspend fun list(
        unreadOnly: Boolean,
        page: Int,
        size: Int = 20,
    ): ApiResult<PageDto<NotificationDto>> =
        safeApiCall { notificationApi.list(unreadOnly, page, size) }

    suspend fun unreadCount(): ApiResult<Long> =
        safeApiCall { notificationApi.unreadCount().count }

    suspend fun markRead(id: Long): ApiResult<Unit> =
        safeApiCall { notificationApi.markRead(id) }

    suspend fun markAllRead(): ApiResult<Unit> =
        safeApiCall { notificationApi.markAllRead() }
}
