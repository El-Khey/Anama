package com.novelrealm.exception;

/**
 * Levée quand une notification n'existe pas — ou n'appartient pas à
 * l'utilisateur connecté, ce qui revient au même vu de l'extérieur : on ne
 * confirme jamais l'existence d'une ressource d'autrui. → 404.
 */
public class NotificationNotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public NotificationNotFoundException(Long id) {
        super("Notification introuvable (id " + id + ")");
    }
}
