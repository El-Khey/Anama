package com.novelrealm.ingestion.source.dto;

import java.util.List;

/**
 * Une page de la liste des chapitres d'un roman (pagination offset/limit).
 *
 * @param items  chapitres de cette page (références légères, sans corps)
 * @param total  nombre total de chapitres du roman chez la source
 * @param offset décalage de cette page
 * @param limit  taille de page demandée
 */
public record SourceChapterPage(List<SourceChapterRef> items, int total, int offset, int limit) {

    /** Reste-t-il des chapitres au-delà de cette page ? */
    public boolean hasMore() {
        return offset + items.size() < total;
    }
}
