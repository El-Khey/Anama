package com.novelrealm.dto.comment;

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
 *
 * <p>{@code reactions} : les réactions emoji posées sur ce message, décomptées par
 * emoji (les plus posées d'abord). {@code myReactions} : celles que le lecteur
 * courant a lui-même posées — le client s'en sert pour surligner ses puces. Un
 * message en pierre tombale n'en porte aucune (elles ont été purgées à sa
 * suppression n'aurait pas de sens de réagir à un vide).
 *
 * <p>{@code likes}/{@code dislikes} : les pouces verts / rouges posés sur ce message.
 * {@code myVote} : le sens de MON vote — {@code +1}, {@code -1}, ou {@code 0} si je
 * n'ai pas voté ; le client colore ses pouces d'après cette valeur.
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
        List<EmojiTallyResponse> reactions,
        List<String> myReactions,
        long likes,
        long dislikes,
        int myVote,
        List<ChapterCommentResponse> replies
) {}
