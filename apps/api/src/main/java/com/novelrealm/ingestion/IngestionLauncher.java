package com.novelrealm.ingestion;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Lance les tâches d'ingestion en ARRIÈRE-PLAN (executor {@code ingestionExecutor}),
 * pour que l'endpoint admin rende la main tout de suite (202) au lieu de bloquer
 * sur un import de plusieurs milliers de chapitres (potentiellement des dizaines
 * de minutes).
 *
 * <p>Bean distinct du contrôleur : {@code @Async} passe par un proxy Spring, qui
 * ne s'applique qu'aux appels ENTRANTS depuis un autre bean — un appel interne
 * à la même classe s'exécuterait de façon synchrone.
 *
 * <p>Chaque tâche attrape ses erreurs : rien ne doit remonter dans le thread de
 * l'executor. La synchro manuelle passe par le même verrou consultatif que le
 * cron (pas de double exécution).
 */
@Component
public class IngestionLauncher {

    private static final Logger log = LoggerFactory.getLogger(IngestionLauncher.class);

    private final NovelSyncService syncService;
    private final PgAdvisoryLock advisoryLock;

    public IngestionLauncher(NovelSyncService syncService, PgAdvisoryLock advisoryLock) {
        this.syncService = syncService;
        this.advisoryLock = advisoryLock;
    }

    /** Import d'un titre précis, en tâche de fond. */
    @Async("ingestionExecutor")
    public void launchImport(String slug) {
        try {
            syncService.importNovel(slug);
        } catch (RuntimeException ex) {
            log.error("Import en arrière-plan de '{}' échoué : {}", slug, ex.getMessage(), ex);
        }
    }

    /** Cycle complet (découverte + maintenance), en tâche de fond, sous verrou. */
    @Async("ingestionExecutor")
    public void launchFullSync() {
        advisoryLock.runIfFree(PgAdvisoryLock.INGESTION_SYNC_KEY, () -> {
            try {
                syncService.runFullSync();
            } catch (RuntimeException ex) {
                log.error("Synchro manuelle interrompue par une erreur : {}", ex.getMessage(), ex);
            }
        });
    }
}
