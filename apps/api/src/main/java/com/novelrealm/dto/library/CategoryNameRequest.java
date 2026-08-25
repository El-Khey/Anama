package com.novelrealm.dto.library;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Corps commun à la création et au renommage d'une étagère : juste le nom. */
public record CategoryNameRequest(
        @NotBlank(message = "Le nom de l'étagère est obligatoire")
        @Size(max = 100, message = "Le nom ne doit pas dépasser 100 caractères")
        String name
) {}
