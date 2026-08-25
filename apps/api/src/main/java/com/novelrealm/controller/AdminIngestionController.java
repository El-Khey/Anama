package com.novelrealm.controller;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.novelrealm.admin.AdminGuard;
import com.novelrealm.dto.ingestion.IngestionJobResponse;
import com.novelrealm.dto.ingestion.IngestionStatusResponse;
import com.novelrealm.ingestion.IngestionLauncher;
import com.novelrealm.ingestion.IngestionProgressTracker;

/**
 * Endpoints d'administration de l'ingestion. Sous {@code /api/} (et non
 * {@code /admin/} nu) pour bénéficier du 401 franc de {@code SecurityConfig} en
 * cas de non-authentification. La chaîne de sécurité exige déjà un JWT valide
 * ({@code anyRequest().authenticated()}) ; {@link AdminGuard} restreint ensuite
 * aux emails de {@code app.admin-emails}.
 *
 * <p>Les deux actions lancent un travail de FOND et renvoient <b>202 Accepted</b>
 * immédiatement : un import peut durer longtemps, on ne bloque pas la requête
 * HTTP. Le détail des compteurs part dans les logs serveur (pas de suivi de job
 * exposé pour l'instant — cf. docs/INGESTION_V2.md, « pistes »).
 */
@RestController
@RequestMapping("/api/admin/ingestion")
public class AdminIngestionController {

    private static final String SOURCE = "chikari";

    private final AdminGuard adminGuard;
    private final IngestionLauncher launcher;
    private final IngestionProgressTracker progress;

    public AdminIngestionController(AdminGuard adminGuard, IngestionLauncher launcher,
            IngestionProgressTracker progress) {
        this.adminGuard = adminGuard;
        this.launcher = launcher;
        this.progress = progress;
    }

    /** Importe (ou complète) un titre précis, tout de suite, sans attendre le cron. */
    @PostMapping("/novels/{slug}")
    public ResponseEntity<IngestionJobResponse> importNovel(
            @PathVariable String slug,
            Authentication authentication) {
        adminGuard.require(authentication);
        // Enqueue AVANT le lancement async : la file est ainsi visible tout de suite,
        // même si l'unique thread d'ingestion est encore occupé par un autre import.
        progress.enqueue(slug);
        try {
            launcher.launchImport(slug);
        } catch (RuntimeException ex) {
            // File de l'executor pleine (trop d'imports empilés) : on retire l'entrée
            // qu'on venait d'ajouter et on le dit franchement à l'appelant.
            progress.dequeue(slug);
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(new IngestionJobResponse(
                    false, SOURCE, slug,
                    "Trop d'imports en attente. Réessaie quand la file se sera vidée."));
        }
        return ResponseEntity.accepted().body(new IngestionJobResponse(
                true, SOURCE, slug,
                "Import de « " + slug + " » ajouté à la file. Suis la progression ci-dessous."));
    }

    /** État courant de l'ingestion (import en cours + file) pour le suivi live. */
    @GetMapping("/status")
    public IngestionStatusResponse status(Authentication authentication) {
        adminGuard.require(authentication);
        IngestionProgressTracker.Snapshot snap = progress.snapshot();
        IngestionProgressTracker.ActiveImport a = snap.active();
        IngestionStatusResponse.ActiveImport active = a == null ? null
                : new IngestionStatusResponse.ActiveImport(
                        a.slug(), a.title(), a.phase(), a.done(), a.total(), a.skipped(), a.startedAt());
        List<IngestionStatusResponse.QueuedImport> queue = snap.queue().stream()
                .map(q -> new IngestionStatusResponse.QueuedImport(q.slug(), q.queuedAt()))
                .toList();
        return new IngestionStatusResponse(active, queue);
    }

    /** Déclenche un cycle complet (découverte + maintenance) à la demande. */
    @PostMapping("/sync")
    public ResponseEntity<IngestionJobResponse> triggerSync(Authentication authentication) {
        adminGuard.require(authentication);
        launcher.launchFullSync();
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(new IngestionJobResponse(
                true, SOURCE, "full-sync",
                "Cycle de synchronisation lancé en arrière-plan (ignoré si un autre est déjà en cours)."));
    }
}
