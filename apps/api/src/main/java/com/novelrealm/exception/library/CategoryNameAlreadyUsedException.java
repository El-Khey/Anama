package com.novelrealm.exception.library;

/**
 * Levée quand l'utilisateur tente de créer/renommer une étagère avec un nom
 * qu'il utilise déjà (contrainte d'unicité (user_id, name)). → 409.
 */
public class CategoryNameAlreadyUsedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public CategoryNameAlreadyUsedException(String name) {
        super("Vous avez déjà une étagère nommée « " + name + " »");
    }
}
