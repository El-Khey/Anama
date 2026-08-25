package com.novelrealm.exception.comment;

import com.novelrealm.exception.passage.InvalidPassageException;

/**
 * Levée quand un commentaire de fin de chapitre est mal formé : ni texte ni GIF,
 * ou une URL de GIF qui ne vient pas du fournisseur. → 400.
 *
 * <p>(Les commentaires de passage ont leur propre {@link InvalidPassageException},
 * qui couvre déjà ce rôle.)
 */
public class InvalidCommentException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public InvalidCommentException(String message) {
        super(message);
    }
}
