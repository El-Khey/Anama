package com.novelrealm.dto;

import java.util.List;

/**
 * L'état des réactions d'un commentaire après un geste : de quoi rafraîchir les puces
 * sous le message sans recharger tout le fil.
 *
 * <p>{@code reactions} : le décompte par emoji (ordre stable, les plus posés d'abord).
 * {@code myReactions} : les emojis que MOI j'ai posés, pour surligner mes puces. Les
 * deux sont ce que portent aussi {@link ChapterCommentResponse} et
 * {@link PassageCommentResponse} pour chaque message d'un fil.
 */
public record CommentReactionsResponse(
        Long commentId,
        List<EmojiTallyResponse> reactions,
        List<String> myReactions
) {}
