package com.novelrealm.ingestion.source.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Un roman vu par une SOURCE d'ingestion, forme NEUTRE (indépendante du JSON
 * du fournisseur). L'adaptateur mappe la réponse brute de la source vers ce
 * record ; le service de synchronisation ne connaît que cette forme.
 *
 * @param sourceId     identifiant STABLE chez la source (clé d'idempotence)
 * @param slug         slug chez la source (sert d'URL de reader + fallback de clé)
 * @param title        titre
 * @param author       auteur unique (déjà réduit depuis la liste d'auteurs)
 * @param description  résumé
 * @param coverUrl     URL absolue de couverture (peut être {@code null})
 * @param rawStatus    statut BRUT de la source (ex. « releasing ») — mappé plus tard
 * @param nsfw         contenu adulte
 * @param chapterCount nombre de chapitres annoncé (signal de fraîcheur)
 * @param latestNumber dernier numéro de chapitre annoncé (signal de fraîcheur)
 * @param lastChapterAt date du dernier chapitre annoncé (signal de fraîcheur)
 * @param genres       noms de genres (find-or-create côté service)
 */
public record SourceNovel(
        long sourceId,
        String slug,
        String title,
        String author,
        String description,
        String coverUrl,
        String rawStatus,
        boolean nsfw,
        int chapterCount,
        BigDecimal latestNumber,
        Instant lastChapterAt,
        List<String> genres) {
}
