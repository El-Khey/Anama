package com.novelrealm.dto.progress;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** Corps de la requête de sauvegarde de la position de reprise (en %). */
public record SaveChapterPositionRequest(
        @NotNull(message = "Le pourcentage est obligatoire")
        @Min(value = 0, message = "Le pourcentage doit être ≥ 0")
        @Max(value = 100, message = "Le pourcentage doit être ≤ 100")
        Integer percent
) {}
