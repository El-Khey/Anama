package com.novelrealm.exception.comment;

/**
 * Levée quand un utilisateur tente de modifier ou de supprimer un commentaire
 * qu'il n'a pas écrit. → 403.
 *
 * <p>Volontairement distincte d'un 404 : le message existe bel et bien, c'est
 * l'auteur qui ne correspond pas. La modération (issue dédiée) introduira plus
 * tard les rôles qui autorisent à agir sur le message d'autrui.
 */
public class CommentNotOwnedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public CommentNotOwnedException() {
        super("Vous ne pouvez modifier que vos propres commentaires");
    }
}
