package com.novelrealm.exception.library;

/**
 * Levée quand on tente d'ajouter un roman déjà présent dans la bibliothèque de
 * l'utilisateur (la clé (user_id, novel_id) doit rester unique). → 409.
 */
public class LibraryEntryAlreadyExistsException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public LibraryEntryAlreadyExistsException(Long novelId) {
        super("Ce roman (id " + novelId + ") est déjà dans votre bibliothèque");
    }
}
