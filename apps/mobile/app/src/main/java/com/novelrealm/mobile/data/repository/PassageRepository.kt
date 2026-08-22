package com.novelrealm.mobile.data.repository

import com.novelrealm.mobile.data.remote.ApiResult
import com.novelrealm.mobile.data.remote.api.PassageApi
import com.novelrealm.mobile.data.remote.dto.ChapterActivityDto
import com.novelrealm.mobile.data.remote.dto.CommentReactionsDto
import com.novelrealm.mobile.data.remote.dto.CreatePassageCommentRequestDto
import com.novelrealm.mobile.data.remote.dto.PassageCommentDto
import com.novelrealm.mobile.data.remote.dto.PassageReactionDto
import com.novelrealm.mobile.data.remote.dto.ReactToCommentRequestDto
import com.novelrealm.mobile.data.remote.dto.ReactToPassageRequestDto
import com.novelrealm.mobile.data.remote.safeApiCall

/**
 * Couche sociale d'un passage : marques en marge, fils, réactions (#41, §4).
 *
 * Le jeu d'emojis est figé ici et **doit rester identique à
 * `PassageSocialService.ALLOWED_EMOJIS` côté back**, ordre compris — c'est lui qui
 * fixe l'ordre d'affichage, pour que les emojis ne changent pas de place quand les
 * compteurs bougent. Le serveur refuse tout emoji hors de cette liste.
 */
class PassageRepository(private val passageApi: PassageApi) {

    suspend fun activity(chapterId: Long): ApiResult<ChapterActivityDto> =
        safeApiCall { passageApi.activity(chapterId) }

    suspend fun thread(chapterId: Long, blockIndex: Int): ApiResult<List<PassageCommentDto>> =
        safeApiCall { passageApi.thread(chapterId, blockIndex) }

    suspend fun comment(
        chapterId: Long,
        blockIndex: Int,
        body: String,
        spoiler: Boolean,
        parentId: Long? = null,
        mentionedUserIds: List<Long> = emptyList(),
        gifUrl: String? = null,
        gifPreviewUrl: String? = null,
    ): ApiResult<PassageCommentDto> = safeApiCall {
        passageApi.comment(
            chapterId,
            CreatePassageCommentRequestDto(
                blockIndex, body, spoiler, parentId,
                mentionedUserIds, gifUrl, gifPreviewUrl,
            ),
        )
    }

    suspend fun react(
        chapterId: Long,
        blockIndex: Int,
        emoji: String,
    ): ApiResult<PassageReactionDto> = safeApiCall {
        passageApi.react(chapterId, ReactToPassageRequestDto(blockIndex, emoji))
    }

    /**
     * Pose ou retire une réaction emoji sur un MESSAGE de passage (pas sur le bloc :
     * voir `react`). Renvoie l'état à jour des réactions du message.
     */
    suspend fun reactToComment(annotationId: Long, emoji: String): ApiResult<CommentReactionsDto> =
        safeApiCall { passageApi.reactToComment(annotationId, ReactToCommentRequestDto(emoji)) }

    suspend fun delete(annotationId: Long): ApiResult<Unit> =
        safeApiCall { passageApi.delete(annotationId) }

    companion object {
        /**
         * Jeu d'emojis fermé — miroir exact du back, ordre compris.
         *
         * <p>Six exactement : `ReactionRow` en fait six tuiles de largeur égale, un
         * septième les comprimerait ou imposerait un défilement horizontal.
         *
         * <p>Le jeu est tourné vers la **lecture** : ❤️ j'adore · 😂 drôle ·
         * 🤯 le retournement · 😭 ça m'a brisé · 🔥 ça envoie · 😱 glaçant.
         *
         * <p>⚠️ Le modifier demande trois gestes, jamais un seul :
         * ici, dans `PassageSocialService.ALLOWED_EMOJIS`, et une migration qui
         * réécrit les réactions déjà posées — le back ne renvoie les compteurs que
         * pour les emojis de cette liste.
         */
        val EMOJIS = listOf("❤️", "😂", "🤯", "😭", "🔥", "😱")

        /** Longueur maximale d'un message de passage — miroir de `MAX_BODY_LENGTH`. */
        const val MAX_BODY_LENGTH = 1_000
    }
}
