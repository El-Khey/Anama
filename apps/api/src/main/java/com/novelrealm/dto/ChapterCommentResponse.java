package com.novelrealm.dto;

import java.time.Instant;
import java.util.List;

/**
 * Un message de fin de chapitre tel qu'il s'affiche. L'auteur est réduit à ce qui
 * sert à le montrer (pseudo + avatar) — jamais son email.
 *
 * <p>{@code mine} est calculé côté serveur à partir du jeton : le client n'a donc
 * pas besoin de connaître son propre identifiant pour savoir quoi rendre
 * modifiable.
 *
 * <p>Un message supprimé qui porte encore des réponses est renvoyé en
 * « pierre tombale » : {@code deleted = true}, corps et auteur vidés, mais ses
 * réponses restent lisibles.
 */
public record ChapterCommentResponse(
        Long id,
        Long userId,
        String pseudo,
        String avatarUrl,
        String body,
        boolean deleted,
        boolean mine,
        boolean edited,
        Instant createdAt,
        Instant updatedAt,
        List<ChapterCommentResponse> replies
) {}
