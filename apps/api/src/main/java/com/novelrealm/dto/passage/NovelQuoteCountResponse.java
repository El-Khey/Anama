package com.novelrealm.dto.passage;

/** Nombre de citations d'un utilisateur pour un roman donné (filtres + compteur de fiche). */
public record NovelQuoteCountResponse(
        Long novelId,
        String novelTitle,
        long count
) {}
