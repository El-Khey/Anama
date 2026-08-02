package com.novelrealm.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Modification de SON propre message. Seul le texte change. */
public record UpdateChapterCommentRequest(
        @NotBlank(message = "Le message ne peut pas être vide")
        @Size(max = 2000, message = "Le message ne peut pas dépasser 2000 caractères")
        String body
) {}
