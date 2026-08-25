package com.novelrealm.exception.common;

/**
 * Levée quand un fichier envoyé (avatar, bannière) est invalide : format non
 * pris en charge, taille dépassée, fichier vide. → 400.
 */
public class InvalidUploadException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public InvalidUploadException(String message) {
        super(message);
    }
}
