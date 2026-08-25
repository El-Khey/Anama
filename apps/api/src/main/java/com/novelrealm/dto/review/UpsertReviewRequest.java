package com.novelrealm.dto.review;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Dépôt ou modification de SON avis sur un roman. La note est obligatoire, le
 * commentaire facultatif (on peut noter sans écrire).
 */
public record UpsertReviewRequest(
        @NotNull(message = "La note est obligatoire")
        @Min(value = 1, message = "La note doit être comprise entre 1 et 5")
        @Max(value = 5, message = "La note doit être comprise entre 1 et 5")
        Integer rating,

        @Size(max = 2000, message = "Le commentaire ne peut pas dépasser 2000 caractères")
        String body
) {}
