package com.novelrealm.dto.user;

import jakarta.validation.constraints.Size;
import tools.jackson.databind.JsonNode;

/**
 * Corps du PATCH /api/users/me. Mise à jour PARTIELLE : chaque champ absent
 * (null) est laissé tel quel. Une bio vide ("") efface la bio.
 */
public record UpdateProfileRequest(
        @Size(min = 3, max = 30, message = "Le pseudo doit faire entre 3 et 30 caractères")
        String pseudo,

        @Size(max = 280, message = "La bio est limitée à 280 caractères")
        String bio,

        // JSON opaque (accent, réglages du lecteur…) — validé côté service.
        JsonNode preferences
) {}
