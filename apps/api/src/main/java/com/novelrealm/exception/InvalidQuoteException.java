package com.novelrealm.exception;

/**
 * Levée quand les coordonnées d'une citation ne désignent rien d'exploitable :
 * bloc hors du chapitre, sélection vide, ou passage trop long. → 400.
 */
public class InvalidQuoteException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public InvalidQuoteException(String message) {
        super(message);
    }
}
