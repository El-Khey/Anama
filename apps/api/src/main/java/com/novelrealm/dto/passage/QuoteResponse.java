package com.novelrealm.dto.passage;

import java.time.Instant;

/**
 * Une citation telle qu'elle s'affiche dans « Mes citations ».
 *
 * <p>Le texte est celui figé à la capture : la carte reste lisible même si le
 * chapitre a changé depuis. L'état de l'ancre n'est PAS renvoyé ici — le vérifier
 * demanderait de charger le texte intégral de chaque chapitre de la page. Il se
 * demande à la carte, au moment où l'on veut réellement y retourner
 * ({@code GET /api/quotes/{id}/anchor}).
 */
public record QuoteResponse(
        Long id,
        String quotedText,
        Long novelId,
        String novelTitle,
        String novelCoverUrl,
        Long chapterId,
        int chapterNumber,
        String chapterTitle,
        Instant createdAt
) {}
