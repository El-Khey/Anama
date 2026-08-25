package com.novelrealm.exception.comment;

/**
 * Levée quand un commentaire de chapitre n'existe pas (ou a déjà été supprimé).
 * → 404.
 */
public class ChapterCommentNotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ChapterCommentNotFoundException(Long commentId) {
        super("Commentaire introuvable (id " + commentId + ")");
    }
}
