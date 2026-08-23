package com.novelrealm.mobile.data.remote.api

import com.novelrealm.mobile.data.remote.dto.ChapterActivityDto
import com.novelrealm.mobile.data.remote.dto.CommentReactionsDto
import com.novelrealm.mobile.data.remote.dto.CommentVotesDto
import com.novelrealm.mobile.data.remote.dto.CreatePassageCommentRequestDto
import com.novelrealm.mobile.data.remote.dto.PassageCommentDto
import com.novelrealm.mobile.data.remote.dto.PassageReactionDto
import com.novelrealm.mobile.data.remote.dto.ReactToCommentRequestDto
import com.novelrealm.mobile.data.remote.dto.ReactToPassageRequestDto
import com.novelrealm.mobile.data.remote.dto.VoteRequestDto
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

// Réactions et commentaires sur un passage (#41, §4).
interface PassageApi {

    /**
     * L'activité de TOUS les blocs du chapitre, en un appel.
     *
     * Un appel par bloc serait intenable : un chapitre compte couramment cinquante
     * paragraphes et le lecteur les fait défiler d'un geste. Les compteurs partent à
     * l'ouverture, les fils ne sont chargés qu'à l'ouverture d'une discussion.
     */
    @GET("api/chapters/{chapterId}/activity")
    suspend fun activity(@Path("chapterId") chapterId: Long): ChapterActivityDto

    @GET("api/chapters/{chapterId}/passages/{block}/comments")
    suspend fun thread(
        @Path("chapterId") chapterId: Long,
        @Path("block") blockIndex: Int,
    ): List<PassageCommentDto>

    @POST("api/chapters/{chapterId}/passages/comments")
    suspend fun comment(
        @Path("chapterId") chapterId: Long,
        @Body body: CreatePassageCommentRequestDto,
    ): PassageCommentDto

    /** Pose, remplace ou retire — le serveur déduit lequel d'après l'état courant. */
    @PUT("api/chapters/{chapterId}/passages/reactions")
    suspend fun react(
        @Path("chapterId") chapterId: Long,
        @Body body: ReactToPassageRequestDto,
    ): PassageReactionDto

    /**
     * Réaction emoji sur un MESSAGE de passage (pas sur le bloc : voir `react`).
     * Pose OU retire — plusieurs emojis par lecteur, façon Discord.
     */
    @PUT("api/passages/comments/{annotationId}/reactions")
    suspend fun reactToComment(
        @Path("annotationId") annotationId: Long,
        @Body body: ReactToCommentRequestDto,
    ): CommentReactionsDto

    /** Pouce vert (+1) ou rouge (-1), ou retrait, sur un MESSAGE de passage. */
    @PUT("api/passages/comments/{annotationId}/vote")
    suspend fun voteComment(
        @Path("annotationId") annotationId: Long,
        @Body body: VoteRequestDto,
    ): CommentVotesDto

    @DELETE("api/passages/annotations/{annotationId}")
    suspend fun delete(@Path("annotationId") annotationId: Long)
}
