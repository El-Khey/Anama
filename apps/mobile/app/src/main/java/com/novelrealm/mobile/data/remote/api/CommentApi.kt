package com.novelrealm.mobile.data.remote.api

import com.novelrealm.mobile.data.remote.dto.ChapterCommentDto
import com.novelrealm.mobile.data.remote.dto.CommentCountDto
import com.novelrealm.mobile.data.remote.dto.CommentReactionsDto
import com.novelrealm.mobile.data.remote.dto.CommentVotesDto
import com.novelrealm.mobile.data.remote.dto.CreateCommentRequestDto
import com.novelrealm.mobile.data.remote.dto.PageDto
import com.novelrealm.mobile.data.remote.dto.ReactToCommentRequestDto
import com.novelrealm.mobile.data.remote.dto.UpdateCommentRequestDto
import com.novelrealm.mobile.data.remote.dto.VoteRequestDto
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

// Messages de fin de chapitre (#41). L'auteur est toujours l'utilisateur connecté
// (résolu côté back depuis le jeton) : aucun id utilisateur ne circule ici.
interface CommentApi {

    /** Fils du chapitre, réponses comprises. `sort` : "recent" (défaut) ou "discussed". */
    @GET("api/chapters/{chapterId}/comments")
    suspend fun getComments(
        @Path("chapterId") chapterId: Long,
        @Query("sort") sort: String = "recent",
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 10,
    ): PageDto<ChapterCommentDto>

    /** Le nombre seul — affiché sous « Fin du chapitre » sans charger un message. */
    @GET("api/chapters/{chapterId}/comments/count")
    suspend fun getCount(@Path("chapterId") chapterId: Long): CommentCountDto

    /**
     * Nombre de messages par chapitre pour tout un roman, en un appel.
     * Clés = ids de chapitre en texte (JSON n'a pas de clé numérique) ; les
     * chapitres sans message sont absents.
     */
    @GET("api/novels/{novelId}/comment-counts")
    suspend fun getCountsByNovel(@Path("novelId") novelId: Long): Map<String, Long>

    @POST("api/chapters/{chapterId}/comments")
    suspend fun create(
        @Path("chapterId") chapterId: Long,
        @Body body: CreateCommentRequestDto,
    ): ChapterCommentDto

    @PATCH("api/comments/{commentId}")
    suspend fun update(
        @Path("commentId") commentId: Long,
        @Body body: UpdateCommentRequestDto,
    ): ChapterCommentDto

    /** Pose OU retire une réaction emoji — le serveur déduit lequel d'après l'état courant. */
    @PUT("api/comments/{commentId}/reactions")
    suspend fun react(
        @Path("commentId") commentId: Long,
        @Body body: ReactToCommentRequestDto,
    ): CommentReactionsDto

    /** Pouce vert (+1) ou rouge (-1), ou retrait — le serveur déduit d'après l'état courant. */
    @PUT("api/comments/{commentId}/vote")
    suspend fun vote(
        @Path("commentId") commentId: Long,
        @Body body: VoteRequestDto,
    ): CommentVotesDto

    @DELETE("api/comments/{commentId}")
    suspend fun delete(@Path("commentId") commentId: Long)
}
