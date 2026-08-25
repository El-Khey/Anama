package com.novelrealm.dto.gif;

import java.util.List;

/**
 * Une page de résultats GIF (issue #45, §5). {@code next} est le curseur opaque
 * du fournisseur : le renvoyer en {@code pos} donne la page suivante ; vide = fin.
 */
public record GifPageResponse(
        List<GifResponse> results,
        String next
) {}
