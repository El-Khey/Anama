package com.novelrealm.dto.ingestion;

import java.time.Instant;
import java.util.List;

/**
 * État courant de l'ingestion, pour le tableau de bord admin (suivi live).
 * « En cours + file » (choix produit) ; pas d'historique.
 *
 * @param active import en cours ({@code null} si rien ne tourne)
 * @param queue  imports en attente de démarrage
 */
public record IngestionStatusResponse(ActiveImport active, List<QueuedImport> queue) {

    /**
     * @param slug      slug importé
     * @param title     titre (provisoirement = slug tant que le détail n'est pas récupéré)
     * @param phase     étape en cours (détail / liste / téléchargement / finalisation)
     * @param done      chapitres présents en base pour ce roman jusqu'ici
     * @param total     chapitres attendus (source) ; 0 si encore inconnu
     * @param skipped   chapitres sautés (verrouillés/vides/décimaux)
     * @param startedAt début de l'import
     */
    public record ActiveImport(
            String slug, String title, String phase,
            int done, int total, int skipped, Instant startedAt) {
    }

    /**
     * @param slug     slug en attente
     * @param queuedAt mise en file
     */
    public record QueuedImport(String slug, Instant queuedAt) {
    }
}
