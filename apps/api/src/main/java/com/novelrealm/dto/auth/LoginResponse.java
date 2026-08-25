package com.novelrealm.dto.auth;

import com.fasterxml.jackson.annotation.JsonUnwrapped;

import com.novelrealm.dto.user.UserResponse;

/**
 * Réponse de connexion. Les champs de {@link UserResponse} sont « aplatis » à la
 * racine du JSON via {@link JsonUnwrapped} pour préserver le contrat du front web
 * (qui lit l'utilisateur au niveau racine et ignore les champs inconnus), tandis
 * que {@code token} est destiné au client mobile ({@code Authorization: Bearer}).
 * Le web s'appuie, lui, sur le cookie httpOnly et ignore {@code token}.
 *
 * <p>Note : {@code @JsonUnwrapped} n'est utilisé qu'en <em>sérialisation</em>
 * (jamais désérialisé), ce qui est pleinement supporté sur un record.
 */
public record LoginResponse(
        @JsonUnwrapped UserResponse user,
        String token
) {}
