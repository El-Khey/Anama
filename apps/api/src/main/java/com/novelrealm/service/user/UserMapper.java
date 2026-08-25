package com.novelrealm.service.user;

import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import com.novelrealm.dto.user.UserResponse;
import com.novelrealm.model.User;

/**
 * Entité {@link User} → {@link UserResponse}. Centralisé ici car la conversion
 * des préférences (String JSON en base → arbre JSON dans la réponse) demande un
 * {@link ObjectMapper}, et le DTO est construit depuis plusieurs controllers.
 */
@Component
public class UserMapper {

    private final ObjectMapper objectMapper;

    public UserMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** Réponse complète, préférences incluses — pour l'utilisateur lui-même. */
    public UserResponse toOwnResponse(User user) {
        return toResponse(user, true);
    }

    /**
     * Réponse « publique » : sans les préférences, et <b>sans l'email</b>. Les
     * mentions (issue #45, §2) rendent les profils atteignables depuis n'importe
     * quel commentaire — l'email y fuyait jusqu'ici, ce qui ne se voyait pas tant
     * que personne n'appelait ces routes.
     */
    public UserResponse toPublicResponse(User user) {
        return toResponse(user, false);
    }

    private UserResponse toResponse(User user, boolean own) {
        return new UserResponse(
                user.getId(),
                user.getPseudo(),
                own ? user.getEmail() : null,
                user.getBio(),
                user.getAvatarUrl(),
                user.getBannerUrl(),
                user.getProvider(),
                own ? parsePreferences(user.getPreferences()) : null,
                user.getCreatedAt());
    }

    /** JSON stocké → arbre Jackson (null si absent ou illisible). */
    private JsonNode parsePreferences(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(raw);
        } catch (Exception e) {
            return null; // préférences corrompues : on les ignore plutôt que de casser /me
        }
    }
}
