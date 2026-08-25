package com.novelrealm.dto.ingestion;

/**
 * Réponse (202 Accepted) d'un déclenchement d'ingestion via l'endpoint admin.
 * L'import réel tourne en tâche de fond (il peut durer longtemps) : on confirme
 * seulement la PRISE EN COMPTE, pas un résultat. Le détail (compteurs) part dans
 * les logs du serveur.
 *
 * @param accepted la demande a-t-elle été acceptée et lancée en arrière-plan
 * @param source   la source concernée (ex. « chikari »)
 * @param target   la cible (slug importé, ou « full-sync » pour un cycle complet)
 * @param message  message lisible pour l'appelant
 */
public record IngestionJobResponse(boolean accepted, String source, String target, String message) {
}
