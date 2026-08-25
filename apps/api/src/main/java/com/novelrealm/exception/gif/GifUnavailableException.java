package com.novelrealm.exception.gif;

/**
 * Levée quand la recherche de GIF ne peut pas répondre : pas de clé KLIPY
 * configurée, ou le fournisseur injoignable. → 503, jamais une 500 : le service est
 * indisponible, l'app n'a rien fait de mal et peut le dire à l'utilisateur.
 */
public class GifUnavailableException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public GifUnavailableException(String message) {
        super(message);
    }
}
