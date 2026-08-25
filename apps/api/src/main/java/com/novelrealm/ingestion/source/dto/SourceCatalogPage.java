package com.novelrealm.ingestion.source.dto;

import java.util.List;

/**
 * Une page du CATALOGUE de la source (pagination offset/limit). Chaque entrée
 * est un {@link SourceNovel} déjà mappé (les champs de détail manquants dans la
 * liste — description complète, genres — sont récupérés via
 * {@code fetchNovel(slug)} au moment de l'import).
 *
 * @param items  romans de cette page
 * @param total  nombre total de romans au catalogue (base du miroir complet)
 * @param offset décalage de cette page
 * @param limit  taille de page demandée
 */
public record SourceCatalogPage(List<SourceNovel> items, int total, int offset, int limit) {

    /** Reste-t-il des romans au-delà de cette page ? */
    public boolean hasMore() {
        return offset + items.size() < total;
    }
}
