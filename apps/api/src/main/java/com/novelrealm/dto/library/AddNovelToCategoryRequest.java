package com.novelrealm.dto.library;

import jakarta.validation.constraints.NotNull;

/** Corps de la requête d'ajout d'un roman dans une étagère. */
public record AddNovelToCategoryRequest(
        @NotNull(message = "L'id du roman est obligatoire")
        Long novelId
) {}
