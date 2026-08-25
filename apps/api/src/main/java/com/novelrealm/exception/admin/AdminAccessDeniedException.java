package com.novelrealm.exception.admin;

/**
 * L'utilisateur est authentifié mais ne figure pas dans l'allow-list des admins
 * ({@code app.admin-emails}). Mappé en 403 par le gestionnaire global.
 *
 * <p>Mécanisme d'intérim : le projet n'a pas (encore) de système de rôles — les
 * authorities JWT sont vides et le principal est l'email. La liste d'emails en
 * config est donc la porte d'entrée admin, fail-closed (liste vide ⇒ personne).
 */
public class AdminAccessDeniedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public AdminAccessDeniedException() {
        super("Accès réservé à l'administration.");
    }
}
