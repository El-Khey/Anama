package com.novelrealm.ingestion;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Réglages de l'ingestion V2 (préfixe {@code novelrealm.ingestion}). Tous ont
 * un défaut raisonnable dans {@code application.yml} et sont surchargeables par
 * variable d'environnement — un changement de source ou de rythme est une
 * affaire de config, pas de redéploiement. Voir docs/INGESTION_V2.md.
 */
@ConfigurationProperties(prefix = "novelrealm.ingestion")
public class IngestionProperties {

    /** Coupe-circuit global : à {@code false}, le job planifié ne fait rien. */
    private boolean enabled = true;

    /** Expression cron du sync planifié (défaut : 03:00 quotidien). */
    private String cron = "0 0 3 * * *";

    /** Pause (ms) entre deux téléchargements de corps de chapitre (politesse). */
    private long delayMs = 500;

    /** Taille de page pour parcourir le catalogue de la source. */
    private int catalogPageSize = 24;

    /**
     * Débit du miroir : nombre MAX de NOUVEAUX titres importés par exécution.
     * Évite qu'un premier run n'essaie d'aspirer tout le catalogue d'un coup.
     */
    private int discoveryCap = 100;

    /** La découverte automatique saute-t-elle les titres adultes ? */
    private boolean skipNsfw = true;

    /** Nombre de nouvelles tentatives sur erreur transitoire (429/5xx/timeout). */
    private int maxRetries = 3;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getCron() {
        return cron;
    }

    public void setCron(String cron) {
        this.cron = cron;
    }

    public long getDelayMs() {
        return delayMs;
    }

    public void setDelayMs(long delayMs) {
        this.delayMs = delayMs;
    }

    public int getCatalogPageSize() {
        return catalogPageSize;
    }

    public void setCatalogPageSize(int catalogPageSize) {
        this.catalogPageSize = catalogPageSize;
    }

    public int getDiscoveryCap() {
        return discoveryCap;
    }

    public void setDiscoveryCap(int discoveryCap) {
        this.discoveryCap = discoveryCap;
    }

    public boolean isSkipNsfw() {
        return skipNsfw;
    }

    public void setSkipNsfw(boolean skipNsfw) {
        this.skipNsfw = skipNsfw;
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public void setMaxRetries(int maxRetries) {
        this.maxRetries = maxRetries;
    }
}
