package com.novelrealm.dto.library;

import jakarta.validation.constraints.NotNull;

import com.novelrealm.model.LibraryEntry.ReadingStatus;

/** Corps de la requête de changement de statut de lecture d'une entrée. */
public record UpdateLibraryStatusRequest(
        @NotNull(message = "Le statut est obligatoire")
        ReadingStatus status
) {}
