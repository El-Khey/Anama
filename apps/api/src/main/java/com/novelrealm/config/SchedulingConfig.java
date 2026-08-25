package com.novelrealm.config;

import java.util.concurrent.Executor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import com.novelrealm.ingestion.IngestionProperties;

/**
 * Active la planification ({@code @Scheduled}) et l'exécution asynchrone
 * ({@code @Async}) pour l'ingestion, et lie {@link IngestionProperties}.
 *
 * <p>Isolé dans sa propre config (plutôt que sur la classe {@code main}) pour
 * rester facile à désactiver en test et pour garder la responsabilité groupée.
 *
 * <p>L'executor {@code ingestionExecutor} est volontairement PETIT (1 thread) :
 * l'ingestion est un travail de fond séquentiel et poli (throttlé), pas une
 * charge à paralléliser. Il sert surtout à ce que l'endpoint admin rende la main
 * immédiatement (202) au lieu de bloquer sur un import de plusieurs milliers de
 * chapitres. La file est bornée : on ne laisse pas s'empiler des imports à
 * l'infini.
 */
@Configuration
@EnableScheduling
@EnableAsync
@EnableConfigurationProperties(IngestionProperties.class)
public class SchedulingConfig {

    @Bean(name = "ingestionExecutor")
    public Executor ingestionExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("ingestion-");
        // File pleine → on rejette sans bruit plutôt que de faire porter la tâche
        // par le thread web (qui doit rester libre).
        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.AbortPolicy());
        executor.initialize();
        return executor;
    }
}
