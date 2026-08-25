package com.novelrealm.dto.common;

import java.util.List;
import org.springframework.data.domain.Page;

/**
 * Enveloppe de pagination renvoyée au front : le contenu de la page + les
 * métadonnées (page courante, taille, total). Générique et réutilisable pour
 * toute liste paginée.
 */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
    /** Construit la réponse à partir d'une {@link Page} Spring Data déjà mappée en DTO. */
    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }
}
