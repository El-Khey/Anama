package com.novelrealm.exception.auth;

public class InvalidCredentialsException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public InvalidCredentialsException() {
        super("Email ou mot de passe incorrect");
    }
}