package com.novelrealm.dto.library;

import jakarta.validation.constraints.NotNull;

import com.novelrealm.model.LibraryEntry.ReadingStatus;

/**
 * Corps de la requête d'ajout d'un roman à la bibliothèque.
 *
 * <p>Le statut est optionnel : s'il est absent, le service applique
 * {@code PLAN_TO_READ} par défaut.
 */
public record AddLibraryEntryRequest(
        @NotNull(message = "L'id du roman est obligatoire")
        Long novelId,

        ReadingStatus status
) {}
