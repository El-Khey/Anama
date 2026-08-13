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
 *
 * <p>{@code mentions} (issue #45, §2) : qui est visé par les {@code @pseudo} du
 * texte — le client s'en sert pour les mettre en évidence et les rendre
 * cliquables. {@code gifUrl}/{@code gifPreviewUrl} (issue #45, §5) : le GIF
 * joint, s'il y en a un ; {@code body} peut alors être vide.
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
        List<MentionResponse> mentions,
        String gifUrl,
        String gifPreviewUrl,
        List<ChapterCommentResponse> replies
) {}
