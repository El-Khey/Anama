package com.novelrealm.dto;

/**
 * La fonctionnalité GIF est-elle configurée sur ce serveur ? (issue #45, §5).
 * L'app le demande une fois et masque le bouton GIF si c'est non — plutôt qu'un
 * bouton qui mène à une erreur.
 */
public record GifAvailabilityResponse(boolean available) {}
