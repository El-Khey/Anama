package com.novelrealm.dto.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Corps du PUT /api/users/me/password. */
public record ChangePasswordRequest(
        @NotBlank(message = "Le mot de passe actuel est obligatoire")
        String currentPassword,

        @NotBlank(message = "Le nouveau mot de passe est obligatoire")
        @Size(min = 8, message = "Le nouveau mot de passe doit faire au moins 8 caractères")
        String newPassword
) {}
