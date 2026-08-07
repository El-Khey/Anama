package com.novelrealm.dto;

import java.util.List;

/**
 * État d'un bloc après une réaction : de quoi rafraîchir la marge et la barre sans
 * redemander tout le chapitre.
 */
public record PassageReactionResponse(
        int blockIndex,
        String myEmoji,
        List<EmojiTallyResponse> reactions
) {}
