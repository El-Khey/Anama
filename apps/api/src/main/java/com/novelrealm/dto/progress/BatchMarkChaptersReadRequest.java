package com.novelrealm.dto.progress;

import java.util.List;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

/**
 * Corps de la requête « marquer plusieurs chapitres lus / non lus » (sélection
 * multiple : tout sélectionner, sélectionner en dessous, etc.).
 */
public record BatchMarkChaptersReadRequest(
        @NotEmpty(message = "La liste des chapitres est obligatoire")
        List<Long> chapterIds,

        @NotNull(message = "Le statut de lecture (read) est obligatoire")
        Boolean read
) {}
