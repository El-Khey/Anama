package com.novelrealm.dto.comment;

/** Un emoji et le nombre de lecteurs qui l'ont posé sur un passage. */
public record EmojiTallyResponse(String emoji, long count) {}
