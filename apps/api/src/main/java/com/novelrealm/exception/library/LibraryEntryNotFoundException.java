package com.novelrealm.exception.library;

/**
 * Levée quand on tente de modifier/supprimer une entrée de bibliothèque qui
 * n'existe pas pour ce couple (utilisateur, roman). → 404.
 */
public class LibraryEntryNotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public LibraryEntryNotFoundException(Long novelId) {
        super("Ce roman (id " + novelId + ") n'est pas dans votre bibliothèque");
    }
}
