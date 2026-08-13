package com.novelrealm.mobile.data.remote.api

import com.novelrealm.mobile.data.remote.dto.NotificationDto
import com.novelrealm.mobile.data.remote.dto.PageDto
import com.novelrealm.mobile.data.remote.dto.UnreadCountDto
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

// La cloche (issue #45, §3). Toujours celle de l'utilisateur connecté (jeton).
interface NotificationApi {

    @GET("api/notifications")
    suspend fun list(
        @Query("unreadOnly") unreadOnly: Boolean = false,
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 20,
    ): PageDto<NotificationDto>

    /** Le badge seul — un COUNT côté serveur, appelé à chaque retour au premier plan. */
    @GET("api/notifications/unread-count")
    suspend fun unreadCount(): UnreadCountDto

    @POST("api/notifications/{id}/read")
    suspend fun markRead(@Path("id") id: Long)

    @POST("api/notifications/read-all")
    suspend fun markAllRead()
}
