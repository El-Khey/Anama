package com.novelrealm.exception.user;

/**
 * Levée quand un compte Google (sans mot de passe local) tente de changer de
 * mot de passe. → 409.
 */
public class PasswordChangeNotAllowedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public PasswordChangeNotAllowedException() {
        super("Ce compte utilise la connexion Google : le mot de passe se gère chez Google");
    }
}
