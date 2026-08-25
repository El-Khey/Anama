package com.novelrealm.ingestion;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.stereotype.Component;

/**
 * Suit en MÉMOIRE l'état de l'ingestion pour l'exposer à la page admin : quel
 * import est en cours (avec sa progression), et quels imports attendent dans la
 * file. Thread-safe : l'ingestion écrit depuis le thread {@code ingestion-1},
 * l'endpoint de statut lit depuis un thread web.
 *
 * <p>Volontairement éphémère (pas de persistance) : au redémarrage, l'état repart
 * à vide — l'import async ne survit pas non plus au redémarrage. Pas d'historique
 * ici (choix produit : « en cours + file » seulement).
 */
@Component
public class IngestionProgressTracker {

    /** Étape d'un import en cours, publiée au fil de l'eau. */
    public record ActiveImport(
            String slug,
            String title,
            String phase,       // "fetching-detail" | "listing-chapters" | "downloading" | "finishing"
            int done,           // chapitres nouvellement enregistrés jusqu'ici
            int total,          // chapitres attendus (source), 0 si inconnu
            int skipped,        // verrouillés/vides + décimaux sautés
            Instant startedAt) {
    }

    /** Un titre en attente dans la file (déclenché mais pas encore démarré). */
    public record QueuedImport(String slug, Instant queuedAt) {
    }

    /** Photo instantanée renvoyée à l'admin. */
    public record Snapshot(ActiveImport active, List<QueuedImport> queue) {
    }

    private final AtomicReference<ActiveImport> active = new AtomicReference<>(null);
    private final ConcurrentLinkedQueue<QueuedImport> queue = new ConcurrentLinkedQueue<>();

    // ── Écriture (côté ingestion) ───────────────────────────────────────────

    /** Un import est mis en file (appelé au moment du POST). */
    public void enqueue(String slug) {
        queue.add(new QueuedImport(slug, Instant.now()));
    }

    /** Retire un slug de la file (ex. le lancement async a été rejeté). */
    public void dequeue(String slug) {
        queue.removeIf(q -> q.slug().equals(slug));
    }

    /** Un import démarre : on le retire de la file et il devient l'actif. */
    public void start(String slug, String title, int total) {
        queue.removeIf(q -> q.slug().equals(slug));
        active.set(new ActiveImport(slug, title, "fetching-detail", 0, total, 0, Instant.now()));
    }

    /** Met à jour la phase/le titre/le total sans toucher aux compteurs. */
    public void phase(String phase, String title, int total) {
        active.updateAndGet(a -> a == null ? null
                : new ActiveImport(a.slug(), title != null ? title : a.title(), phase,
                        a.done(), total >= 0 ? total : a.total(), a.skipped(), a.startedAt()));
    }

    /**
     * Incrémente les compteurs de progression (chapitres enregistrés / sautés)
     * SANS toucher à la phase — celle-ci n'est autoritative que via {@link #phase}.
     * Évite qu'un pré-remplissage des compteurs n'affiche « téléchargement » avant
     * l'heure.
     */
    public void progress(int doneDelta, int skippedDelta) {
        active.updateAndGet(a -> a == null ? null
                : new ActiveImport(a.slug(), a.title(), a.phase(),
                        a.done() + doneDelta, a.total(), a.skipped() + skippedDelta, a.startedAt()));
    }

    /** L'import actif se termine (succès ou échec) : on efface l'actif. */
    public void finish(String slug) {
        active.updateAndGet(a -> (a != null && a.slug().equals(slug)) ? null : a);
    }

    // ── Lecture (côté admin) ────────────────────────────────────────────────

    public Snapshot snapshot() {
        return new Snapshot(active.get(), List.copyOf(queue));
    }
}
