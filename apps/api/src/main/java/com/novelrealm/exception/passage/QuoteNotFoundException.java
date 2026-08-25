package com.novelrealm.exception.passage;

/**
 * Levée quand une citation n'existe pas, ou n'appartient pas à l'utilisateur qui la
 * demande. → 404.
 *
 * <p>Volontairement un 404 et non un 403 : une collection de citations est privée,
 * et distinguer « ça n'existe pas » de « ce n'est pas à toi » révélerait déjà
 * l'existence de la citation d'un autre.
 */
public class QuoteNotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public QuoteNotFoundException(Long quoteId) {
        super("Citation introuvable (id " + quoteId + ")");
    }
}
