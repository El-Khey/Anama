package com.novelrealm.exception.user;

/**
 * Levée quand un champ du profil est invalide après nettoyage (pseudo vide,
 * préférences trop volumineuses…). → 400.
 */
public class InvalidProfileFieldException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public InvalidProfileFieldException(String message) {
        super(message);
    }
}
