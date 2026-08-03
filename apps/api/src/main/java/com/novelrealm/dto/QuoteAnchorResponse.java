package com.novelrealm.dto;

/**
 * Où retrouver une citation dans la version ACTUELLE de son chapitre.
 *
 * <p>{@code alive = false} signifie que le bloc d'origine a disparu (chapitre
 * réécrit ou ré-ingéré) : la citation reste lisible dans la collection, mais on ne
 * peut plus y retourner. On préfère le dire que renvoyer le lecteur au mauvais
 * endroit.
 */
public record QuoteAnchorResponse(
        boolean alive,
        int blockIndex,
        Long chapterId,
        Long novelId
) {}
