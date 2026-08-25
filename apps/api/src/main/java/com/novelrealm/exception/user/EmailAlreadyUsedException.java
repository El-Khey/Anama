package com.novelrealm.exception.user;

public class EmailAlreadyUsedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public EmailAlreadyUsedException(String email) {
        super("L'email " + email + " est déjà utilisé");
    }
}