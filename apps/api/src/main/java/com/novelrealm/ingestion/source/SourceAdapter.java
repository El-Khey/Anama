package com.novelrealm.ingestion.source;

import java.math.BigDecimal;

import com.novelrealm.ingestion.source.dto.SourceCatalogPage;
import com.novelrealm.ingestion.source.dto.SourceChapterBody;
import com.novelrealm.ingestion.source.dto.SourceChapterPage;
import com.novelrealm.ingestion.source.dto.SourceNovel;

/**
 * Abstraction d'une SOURCE de romans (aujourd'hui chikari.moe). Toute la
 * connaissance du protocole/JSON d'un fournisseur vit dans l'implémentation ;
 * le service de synchronisation ne dépend que de cette interface et des DTO
 * neutres du package {@code dto}.
 *
 * <p>Une seule implémentation existe pour l'instant ({@link ChikariSourceAdapter}),
 * injectée par type. L'interface n'existe pas pour faire joli : elle donne un
 * sens à la colonne {@code novels.source} et rend l'ajout d'une 2ᵉ source
 * mécanique. Pas de registry/factory tant qu'il n'y a qu'une source.
 *
 * <p><b>Contrat d'erreur.</b> Toute méthode peut lever
 * {@link com.novelrealm.ingestion.SourceUnavailableException} si la source est
 * injoignable après tentatives. Un {@code fetch} d'un roman/chapitre inexistant
 * (404) est signalé de la même façon : c'est au service d'attraper et de
 * continuer.
 */
public interface SourceAdapter {

    /** Clé courte de la source, stockée dans {@code novels.source} (ex. « chikari »). */
    String sourceKey();

    /**
     * Une page du catalogue.
     *
     * @param sort   critère de tri de la source (ex. « added ») ; {@code null} = tri par défaut
     * @param offset décalage (pagination)
     * @param limit  taille de page
     */
    SourceCatalogPage listCatalog(String sort, int offset, int limit);

    /** Le détail complet d'un roman (description, genres, signaux de fraîcheur…). */
    SourceNovel fetchNovel(String slug);

    /** Une page de la liste des chapitres d'un roman (références légères, sans corps). */
    SourceChapterPage listChapters(String slug, int offset, int limit);

    /** Le corps d'un chapitre précis (le texte). */
    SourceChapterBody fetchChapterBody(String slug, BigDecimal number);
}
