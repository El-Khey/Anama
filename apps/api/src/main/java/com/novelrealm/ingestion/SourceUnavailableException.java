package com.novelrealm.ingestion;

/**
 * La source d'ingestion est injoignable ou renvoie une erreur non récupérable
 * (après épuisement des tentatives). Levée par l'adaptateur ; attrapée par le
 * service (par-roman) et par le job planifié (au sommet) pour qu'une panne de
 * la source ne fasse JAMAIS tomber l'application — le prochain cycle réessaiera.
 */
public class SourceUnavailableException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public SourceUnavailableException(String message) {
        super(message);
    }

    public SourceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
