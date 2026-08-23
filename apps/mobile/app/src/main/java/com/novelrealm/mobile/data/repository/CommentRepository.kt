package com.novelrealm.mobile.data.repository

import com.novelrealm.mobile.data.remote.ApiResult
import com.novelrealm.mobile.data.remote.api.CommentApi
import com.novelrealm.mobile.data.remote.dto.ChapterCommentDto
import com.novelrealm.mobile.data.remote.dto.CommentReactionsDto
import com.novelrealm.mobile.data.remote.dto.CommentVotesDto
import com.novelrealm.mobile.data.remote.dto.CreateCommentRequestDto
import com.novelrealm.mobile.data.remote.dto.PageDto
import com.novelrealm.mobile.data.remote.dto.ReactToCommentRequestDto
import com.novelrealm.mobile.data.remote.dto.UpdateCommentRequestDto
import com.novelrealm.mobile.data.remote.dto.VoteRequestDto
import com.novelrealm.mobile.data.remote.safeApiCall

// Messages de fin de chapitre : fils paginés, compteurs, et gestion des siens.
class CommentRepository(private val commentApi: CommentApi) {

    /**
     * Une page de fils. Le tri est figé sur « récents » côté app : le back sait aussi
     * trier par activité (`discussed`), mais l'écran n'expose pas encore ce choix.
     */
    suspend fun getComments(
        chapterId: Long,
        page: Int,
        size: Int,
    ): ApiResult<PageDto<ChapterCommentDto>> =
        safeApiCall { commentApi.getComments(chapterId, sort = "recent", page = page, size = size) }

    suspend fun getCount(chapterId: Long): ApiResult<Long> =
        safeApiCall { commentApi.getCount(chapterId).count }

    /**
     * Compteurs d'un roman entier, indexés par id de chapitre. Le back renvoie des
     * clés textuelles (JSON) ; la conversion en `Long` est faite ici pour que les
     * écrans manipulent directement des ids.
     */
    suspend fun getCountsByNovel(novelId: Long): ApiResult<Map<Long, Long>> =
        safeApiCall {
            commentApi.getCountsByNovel(novelId)
                .mapNotNull { (key, count) -> key.toLongOrNull()?.let { it to count } }
                .toMap()
        }

    /**
     * Publie un message. `mentionedUserIds` : résolus par l'autocomplétion (le
     * serveur re-vérifie tout). `gifUrl`/`gifPreviewUrl` : GIF joint —
     * `body` peut alors être vide, jamais les deux à la fois.
     */
    suspend fun create(
        chapterId: Long,
        body: String,
        parentId: Long?,
        mentionedUserIds: List<Long> = emptyList(),
        gifUrl: String? = null,
        gifPreviewUrl: String? = null,
    ): ApiResult<ChapterCommentDto> =
        safeApiCall {
            commentApi.create(
                chapterId,
                CreateCommentRequestDto(body, parentId, mentionedUserIds, gifUrl, gifPreviewUrl),
            )
        }

    suspend fun update(commentId: Long, body: String): ApiResult<ChapterCommentDto> =
        safeApiCall { commentApi.update(commentId, UpdateCommentRequestDto(body)) }

    /** Pose ou retire une réaction emoji ; renvoie l'état à jour des réactions du message. */
    suspend fun react(commentId: Long, emoji: String): ApiResult<CommentReactionsDto> =
        safeApiCall { commentApi.react(commentId, ReactToCommentRequestDto(emoji)) }

    /** Vote +1/-1 (ou retrait) ; renvoie l'état à jour des pouces du message. */
    suspend fun vote(commentId: Long, value: Int): ApiResult<CommentVotesDto> =
        safeApiCall { commentApi.vote(commentId, VoteRequestDto(value)) }

    suspend fun delete(commentId: Long): ApiResult<Unit> =
        safeApiCall { commentApi.delete(commentId) }
}
