package com.novelrealm.dto;

import java.time.Instant;
import java.util.List;

/**
 * Un message accroché à un passage. L'auteur est réduit à ce qui sert à le montrer
 * (pseudo + avatar) — jamais son email.
 *
 * <p>{@code mine} est calculé côté serveur à partir du jeton : le client n'a donc pas
 * besoin de connaître son propre identifiant pour savoir quoi rendre supprimable.
 *
 * <p>{@code replies} n'est rempli que sur les messages racines — l'arbre ne descend
 * jamais plus bas d'un niveau, et une réponse porte donc toujours une liste vide.
 *
 * <p>{@code mentions} (issue #45, §2) : qui est visé par les {@code @pseudo} du
 * texte. {@code gifUrl}/{@code gifPreviewUrl} (issue #45, §5) : le GIF joint ;
 * {@code body} peut alors être vide.
 *
 * <p>{@code reactions} : les réactions emoji posées sur ce message, décomptées par
 * emoji (les plus posées d'abord). {@code myReactions} : celles que le lecteur
 * courant a lui-même posées, pour surligner ses puces côté client.
 *
 * <p>{@code likes}/{@code dislikes} : les pouces verts / rouges posés sur ce message.
 * {@code myVote} : le sens de MON vote — {@code +1}, {@code -1}, ou {@code 0} si je
 * n'ai pas voté ; le client colore ses pouces d'après cette valeur.
 */
public record PassageCommentResponse(
        Long id,
        Long userId,
        String pseudo,
        String avatarUrl,
        String body,
        boolean spoiler,
        boolean mine,
        Instant createdAt,
        List<MentionResponse> mentions,
        String gifUrl,
        String gifPreviewUrl,
        List<EmojiTallyResponse> reactions,
        List<String> myReactions,
        long likes,
        long dislikes,
        int myVote,
        List<PassageCommentResponse> replies
) {}
