package com.novelrealm.ingestion;

/**
 * Petits records de comptes rendus d'ingestion — pour les logs et la réponse de
 * l'endpoint admin. Regroupés ici pour ne pas éparpiller cinq mini-fichiers.
 */
public final class SyncResults {

    private SyncResults() {
    }

    /**
     * Résultat de l'import d'UN roman.
     *
     * @param slug           slug importé
     * @param created        le roman a-t-il été créé (vs déjà présent puis mis à jour)
     * @param importedChapters chapitres nouvellement enregistrés
     * @param skippedLocked  chapitres sautés car verrouillés / corps vide
     * @param skippedDecimal chapitres sautés car numéro non entier
     * @param failed         chapitres dont le téléchargement a échoué (source instable)
     */
    public record ImportResult(
            String slug,
            boolean created,
            int importedChapters,
            int skippedLocked,
            int skippedDecimal,
            int failed) {
    }

    /**
     * Résultat de la maintenance des romans déjà en base.
     *
     * @param novelsChecked  romans examinés
     * @param novelsUnchanged romans inchangés (court-circuit, aucun appel chapitre)
     * @param novelsUpdated  romans ayant reçu au moins un nouveau chapitre
     * @param importedChapters total des chapitres ajoutés
     * @param failed         romans en échec (source instable) — run poursuivi
     */
    public record SyncResult(
            int novelsChecked,
            int novelsUnchanged,
            int novelsUpdated,
            int importedChapters,
            int failed) {
    }

    /**
     * Résultat de la découverte de nouveaux titres.
     *
     * @param scanned romans du catalogue examinés
     * @param added   nouveaux titres importés
     * @param failed  imports en échec
     */
    public record DiscoverResult(int scanned, int added, int failed) {
    }

    /**
     * Bilan d'un cycle complet (découverte + maintenance), renvoyé au scheduler
     * et à l'endpoint admin de synchro manuelle.
     */
    public record SyncRunSummary(DiscoverResult discovery, SyncResult maintenance) {
    }
}
