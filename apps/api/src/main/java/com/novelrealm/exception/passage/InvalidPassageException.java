package com.novelrealm.exception.passage;

/**
 * Levée quand une annotation de passage ne désigne rien d'exploitable : bloc hors du
 * chapitre, message vide ou trop long, emoji hors du jeu autorisé. → 400.
 */
public class InvalidPassageException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public InvalidPassageException(String message) {
        super(message);
    }
}
