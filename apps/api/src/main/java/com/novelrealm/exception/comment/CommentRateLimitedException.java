package com.novelrealm.exception.comment;

/**
 * Levée quand un utilisateur enchaîne les messages trop vite. → 429.
 *
 * <p>Garde-fou minimal contre la rafale : il ne remplace pas la modération
 * (issue dédiée), il empêche juste qu'un fil soit noyé en quelques secondes.
 */
public class CommentRateLimitedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public CommentRateLimitedException(long seconds) {
        super("Doucement — attends " + seconds + " secondes avant de publier à nouveau");
    }
}
