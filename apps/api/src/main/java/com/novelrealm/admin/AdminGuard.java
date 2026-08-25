package com.novelrealm.admin;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import com.novelrealm.exception.admin.AdminAccessDeniedException;

/**
 * Porte d'entrée « admin » : autorise uniquement les emails listés dans
 * {@code app.admin-emails} (séparés par des virgules). Comparaison insensible à
 * la casse.
 *
 * <p><b>Fail-closed.</b> Liste vide (défaut) ⇒ personne n'est admin. C'est un
 * mécanisme d'INTÉRIM : le projet n'a pas de système de rôles (authorities JWT
 * vides, principal = email). Le jour où un vrai modèle de rôles arrive, ce garde
 * disparaît au profit de {@code @PreAuthorize}. En attendant, c'est minimal,
 * surchargeable par environnement, et sans surface DB.
 *
 * <p>Placé dans un package neutre ({@code admin}) plutôt que dans {@code ingestion} :
 * c'est une brique d'autorisation générale (l'ingestion en est le premier usage,
 * mais {@code UserMapper} s'en sert aussi pour exposer le drapeau {@code isAdmin}).
 */
@Component
public class AdminGuard {

    private final Set<String> adminEmails;

    public AdminGuard(@Value("${app.admin-emails:}") String adminEmailsCsv) {
        this.adminEmails = parse(adminEmailsCsv);
    }

    /**
     * L'email donné est-il admin ? Insensible à la casse ; {@code null}/vide ⇒ non.
     */
    public boolean isAdmin(String email) {
        if (email == null || email.isBlank()) {
            return false;
        }
        return adminEmails.contains(email.strip().toLowerCase());
    }

    /**
     * Vérifie que l'appelant est un admin.
     *
     * @throws AdminAccessDeniedException si l'email n'est pas dans l'allow-list
     */
    public void require(Authentication authentication) {
        if (authentication == null || !isAdmin(authentication.getName())) {
            throw new AdminAccessDeniedException();
        }
    }

    private static Set<String> parse(String csv) {
        if (csv == null || csv.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(csv.split(","))
                .map(String::strip)
                .filter(s -> !s.isEmpty())
                .map(String::toLowerCase)
                .collect(Collectors.toUnmodifiableSet());
    }
}
