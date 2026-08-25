package com.novelrealm.exception.chapter;

public class ChapterNotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ChapterNotFoundException(Long id) {
        super("Aucun chapitre trouvé avec l'id " + id);
    }

}
