package com.novelrealm.exception.review;

/**
 * Levée quand un utilisateur tente de consulter ou supprimer un avis qu'il n'a
 * pas déposé sur ce roman. → 404.
 */
public class ReviewNotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ReviewNotFoundException(Long novelId) {
        super("Vous n'avez pas encore donné d'avis sur ce roman (id " + novelId + ")");
    }
}
