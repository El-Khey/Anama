package com.novelrealm.dto.progress;

import jakarta.validation.constraints.NotNull;

/** Corps de la requête « marquer un chapitre lu / non lu ». */
public record MarkChapterReadRequest(
        @NotNull(message = "Le statut de lecture (read) est obligatoire")
        Boolean read
) {}
