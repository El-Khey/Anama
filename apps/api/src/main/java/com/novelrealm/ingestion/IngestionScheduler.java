package com.novelrealm.ingestion;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Déclencheur planifié du miroir chikari. À l'heure du cron
 * ({@code novelrealm.ingestion.cron}), lance un cycle complet — sous verrou
 * consultatif pour éviter les doubles exécutions (multi-replicas / chevauchement
 * avec un déclenchement manuel).
 *
 * <p><b>Rien ne peut faire tomber l'application depuis ici.</b> Le cycle attrape
 * déjà ses erreurs par roman ; ce niveau ajoute un filet ultime autour de tout,
 * pour qu'une panne de la source ne devienne jamais une exception non capturée
 * dans un thread de planification.
 */
@Component
public class IngestionScheduler {

    private static final Logger log = LoggerFactory.getLogger(IngestionScheduler.class);

    private final NovelSyncService syncService;
    private final PgAdvisoryLock advisoryLock;
    private final IngestionProperties props;

    public IngestionScheduler(NovelSyncService syncService, PgAdvisoryLock advisoryLock,
            IngestionProperties props) {
        this.syncService = syncService;
        this.advisoryLock = advisoryLock;
        this.props = props;
    }

    @Scheduled(cron = "${novelrealm.ingestion.cron:0 0 3 * * *}")
    public void scheduledSync() {
        if (!props.isEnabled()) {
            log.debug("Ingestion désactivée (novelrealm.ingestion.enabled=false) — cycle ignoré.");
            return;
        }
        advisoryLock.runIfFree(PgAdvisoryLock.INGESTION_SYNC_KEY, () -> {
            try {
                syncService.runFullSync();
            } catch (RuntimeException ex) {
                // Filet ultime : on ne laisse JAMAIS remonter une exception dans le
                // thread du planificateur. Le prochain cron réessaiera.
                log.error("Cycle d'ingestion planifié interrompu par une erreur : {}", ex.getMessage(), ex);
            }
        });
    }
}
