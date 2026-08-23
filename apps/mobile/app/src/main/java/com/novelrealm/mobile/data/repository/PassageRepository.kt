package com.novelrealm.mobile.data.repository

import com.novelrealm.mobile.data.remote.ApiResult
import com.novelrealm.mobile.data.remote.api.PassageApi
import com.novelrealm.mobile.data.remote.dto.ChapterActivityDto
import com.novelrealm.mobile.data.remote.dto.CommentReactionsDto
import com.novelrealm.mobile.data.remote.dto.CommentVotesDto
import com.novelrealm.mobile.data.remote.dto.CreatePassageCommentRequestDto
import com.novelrealm.mobile.data.remote.dto.PassageCommentDto
import com.novelrealm.mobile.data.remote.dto.PassageReactionDto
import com.novelrealm.mobile.data.remote.dto.ReactToCommentRequestDto
import com.novelrealm.mobile.data.remote.dto.ReactToPassageRequestDto
import com.novelrealm.mobile.data.remote.dto.VoteRequestDto
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

    /** Vote +1/-1 (ou retrait) sur un MESSAGE de passage ; renvoie l'état à jour des pouces. */
    suspend fun voteComment(annotationId: Long, value: Int): ApiResult<CommentVotesDto> =
        safeApiCall { passageApi.voteComment(annotationId, VoteRequestDto(value)) }

    suspend fun delete(annotationId: Long): ApiResult<Unit> =
        safeApiCall { passageApi.delete(annotationId) }

    companion object {
        /**
         * Les six emojis de la **barre rapide** de réaction — miroir du back
         * (`PassageSocialService.ALLOWED_EMOJIS`), ordre compris.
         *
         * <p>Ce n'est plus un jeu FERMÉ : les réactions de bloc sont désormais
         * multi-emoji, le clavier complet s'ouvre via le bouton « + ». Ces six-là ne
         * sont que le raccourci proposé d'emblée. La barre elle-même est rendue à partir
         * de `EmojiCatalog.QUICK` (même liste) ; cette constante reste le point de
         * vérité côté data, alignée sur le back.
         *
         * <p>Le jeu est tourné vers la **lecture** : ❤️ j'adore · 😂 drôle ·
         * 🤯 le retournement · 😭 ça m'a brisé · 🔥 ça envoie · 😱 glaçant.
         */
        val EMOJIS = listOf("❤️", "😂", "🤯", "😭", "🔥", "😱")

        /** Longueur maximale d'un message de passage — miroir de `MAX_BODY_LENGTH`. */
        const val MAX_BODY_LENGTH = 1_000
    }
}
