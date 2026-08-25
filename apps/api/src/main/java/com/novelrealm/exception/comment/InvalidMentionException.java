package com.novelrealm.exception.comment;

/**
 * Levée quand les mentions d'un message ne sont pas acceptables — trop
 * nombreuses, typiquement. Un identifiant inconnu, lui, est simplement ignoré :
 * l'utilisateur visé a pu supprimer son compte entre l'autocomplétion et
 * l'envoi, ce n'est pas une faute du client. → 400.
 */
public class InvalidMentionException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public InvalidMentionException(String message) {
        super(message);
    }
}
