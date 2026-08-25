package com.novelrealm.exception.library;

/**
 * Levée quand une étagère demandée n'existe pas — ou n'appartient pas à
 * l'utilisateur connecté (on ne distingue pas les deux, pour ne pas révéler
 * l'existence des étagères d'autrui). → 404.
 */
public class CategoryNotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public CategoryNotFoundException(Long id) {
        super("Aucune étagère trouvée avec l'id " + id);
    }
}
