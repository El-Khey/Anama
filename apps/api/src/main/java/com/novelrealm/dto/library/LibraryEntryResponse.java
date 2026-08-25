package com.novelrealm.dto.library;

import java.time.Instant;

import com.novelrealm.dto.novel.NovelResponse;
import com.novelrealm.model.LibraryEntry.ReadingStatus;

/**
 * Une entrée de la bibliothèque telle que renvoyée au front : les infos du roman
 * (via {@link NovelResponse}) + le statut de lecture et la date d'ajout propres
 * à l'utilisateur.
 */
public record LibraryEntryResponse(
        NovelResponse novel,
        ReadingStatus status,
        Instant addedAt
) {}
