package com.novelrealm.exception.progress;

/**
 * Levée quand on tente de retirer un favori qui n'existe pas pour ce couple
 * (utilisateur, chapitre). → 404.
 */
public class ChapterFavoriteNotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ChapterFavoriteNotFoundException(Long chapterId) {
        super("Ce chapitre (id " + chapterId + ") n'est pas dans vos favoris");
    }
}
