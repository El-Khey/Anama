package com.novelrealm.exception.user;

public class UserNotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public UserNotFoundException(Long id) {
        super("Aucun utilisateur trouvé avec l'id " + id);
    }

    public UserNotFoundException(String email) {
        super("Aucun utilisateur trouvé avec l'email " + email);
    }
}