package com.novelrealm.dto;

/**
 * Un GIF proposé par la recherche (issue #45, §5).
 *
 * <p>{@code url} est la version animée (format « tinygif » de Tenor : assez
 * légère pour un fil de commentaires), {@code previewUrl} une image FIGÉE du
 * même GIF — c'est elle que l'app affiche par défaut, l'animation ne démarrant
 * qu'à la demande. {@code width}/{@code height} évitent les sauts de mise en
 * page pendant le chargement.
 */
public record GifResponse(
        String id,
        String url,
        String previewUrl,
        int width,
        int height
) {}
