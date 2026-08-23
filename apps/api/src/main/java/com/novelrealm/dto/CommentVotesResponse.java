package com.novelrealm.dto;

/**
 * L'état des votes (pouce vert / pouce rouge) d'un commentaire après un geste : de quoi
 * rafraîchir les deux pouces sous le message sans recharger tout le fil.
 *
 * <p>{@code likes} / {@code dislikes} : les décomptes courants. {@code myVote} : le sens
 * de MON vote — {@code +1} pour, {@code -1} contre, {@code 0} si je n'ai pas voté (ou si
 * je viens de retirer mon vote). Ce sont les trois mêmes valeurs que portent aussi
 * {@link ChapterCommentResponse} et {@link PassageCommentResponse} pour chaque message.
 */
public record CommentVotesResponse(
        Long commentId,
        long likes,
        long dislikes,
        int myVote
) {}
