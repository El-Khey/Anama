package com.novelrealm.exception.passage;

/** Annotation de passage introuvable, ou n'appartenant pas à l'utilisateur. → 404. */
public class PassageAnnotationNotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public PassageAnnotationNotFoundException(Long id) {
        super("Annotation introuvable : " + id);
    }
}
