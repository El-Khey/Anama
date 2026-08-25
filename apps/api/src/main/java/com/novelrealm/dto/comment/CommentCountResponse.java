package com.novelrealm.dto.comment;

/**
 * Nombre de messages d'un chapitre, réponses comprises.
 *
 * <p>Endpoint séparé de la liste à dessein : le lecteur affiche « 42 commentaires »
 * sous « Fin du chapitre » sans avoir à charger le moindre message.
 */
public record CommentCountResponse(long count) {}
