package com.novelrealm.dto.comment;

import java.time.Instant;

/**
 * Un de MES commentaires, dans le flux unifié du profil (issue #45, §4).
 *
 * <p>Les commentaires vivent dans deux tables — fin de chapitre et passage —
 * fusionnées ici en un seul flux trié par date. {@code kind} garde leur nature :
 * l'app en a besoin pour le lien (ouvrir les commentaires du chapitre, ou le fil
 * du passage) et pour la suppression (routes différentes).
 *
 * <p>{@code passageExcerpt} n'existe que pour les commentaires de passage :
 * l'extrait du paragraphe commenté, sans lequel le message est incompréhensible
 * hors contexte. Null si le passage a disparu du chapitre (ré-ingestion) — le
 * message reste listé, seul son point d'ancrage s'est perdu.
 *
 * <p>{@code blockIndex} est l'index RÉSOLU sur la version actuelle du chapitre
 * (pas celui stocké à l'époque) : le lien profond tombe donc toujours juste tant
 * que le passage existe encore.
 */
public record MyCommentResponse(
        Kind kind,
        Long id,
        String body,
        String gifUrl,
        String gifPreviewUrl,
        boolean spoiler,
        boolean reply,
        Long novelId,
        String novelTitle,
        String novelCoverUrl,
        Long chapterId,
        int chapterNumber,
        String chapterTitle,
        Integer blockIndex,
        String passageExcerpt,
        Instant createdAt
) {
    public enum Kind {
        /** Message de fin de chapitre ({@code chapter_comment}). */
        CHAPTER,
        /** Message accroché à un passage ({@code passage_annotation}). */
        PASSAGE,
    }
}
