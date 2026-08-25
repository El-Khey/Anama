package com.novelrealm.dto.user;

/**
 * Un utilisateur dans l'autocomplétion des mentions (issue #45, §2). Le strict
 * minimum pour une ligne de suggestion — surtout pas l'email, ni rien qui ne se
 * voie pas déjà publiquement sur un commentaire.
 */
public record UserSearchResponse(
        Long id,
        String pseudo,
        String avatarUrl
) {}
