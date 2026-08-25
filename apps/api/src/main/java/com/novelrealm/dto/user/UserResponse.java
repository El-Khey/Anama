package com.novelrealm.dto.user;

import java.time.Instant;
import tools.jackson.databind.JsonNode;

import com.novelrealm.model.User.AuthProvider;

/**
 * Utilisateur tel que renvoyé au front (profil enrichi — issue #17).
 *
 * <p>{@code preferences} est du JSON opaque (accent de l'app, réglages du
 * lecteur…) renvoyé tel quel ; il n'est inclus que pour l'utilisateur lui-même
 * (cf. {@link com.novelrealm.service.user.UserMapper}).
 */
public record UserResponse(
        Long id,
        String pseudo,
        String email,
        String bio,
        String avatarUrl,
        String bannerUrl,
        AuthProvider provider,
        JsonNode preferences,
        Instant createdAt,
        /**
         * L'utilisateur fait-il partie de l'allow-list admin ({@code app.admin-emails}) ?
         * Renseigné uniquement dans la réponse « own » (jamais dans la vue publique).
         * Pilote l'affichage du lien Admin côté front. Nommé {@code admin} (sans
         * préfixe {@code is}) pour suivre la convention des autres booléens du
         * projet et éviter l'ambiguïté de sérialisation Jackson ({@code isX → "x"}).
         */
        boolean admin
) {}
