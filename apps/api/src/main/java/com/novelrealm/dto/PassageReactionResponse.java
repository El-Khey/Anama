package com.novelrealm.dto;

import java.util.List;

/**
 * État d'un bloc après une réaction : de quoi rafraîchir la marge et les puces sans
 * redemander tout le chapitre.
 *
 * <p>{@code myReactions} (et non plus un {@code myEmoji} unique) : depuis le passage au
 * multi-emoji, un lecteur peut avoir posé plusieurs emojis sur le même bloc.
 */
public record PassageReactionResponse(
        int blockIndex,
        List<String> myReactions,
        List<EmojiTallyResponse> reactions
) {}
