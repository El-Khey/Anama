package com.novelrealm.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

import com.novelrealm.model.Novel;
import com.novelrealm.model.Novel.NovelStatus;

public interface NovelRepository extends JpaRepository<Novel, Long> {
    // Clé naturelle pour l'ingestion idempotente (un slug = un roman).
    Optional<Novel> findBySlug(String slug);

    // ── Ingestion V2 (chikari) ────────────────────────────────────────────────
    // Vraie clé d'idempotence de l'upsert : (source, source_id). Index unique
    // partiel novels_source_id_uq côté base.
    Optional<Novel> findBySourceAndSourceId(String source, Long sourceId);

    // Tous les romans d'une source donnée — parcourus par la synchro de maintenance.
    java.util.List<Novel> findAllBySource(String source);

    // Un slug est-il déjà pris ? (détection de collision avant création — un slug
    // source pourrait télescoper un titre hérité différent.)
    boolean existsBySlug(String slug);

    /**
     * Roman + ses genres chargés en une requête ({@code @EntityGraph}), pour la
     * fiche détail. Évite une {@code LazyInitializationException} côté controller
     * ({@code open-in-view: false}).
     */
    @EntityGraph(attributePaths = "genres")
    Optional<Novel> findWithGenresById(Long id);

    /**
     * Recherche/filtre du catalogue, paginée. Tous les critères sont optionnels
     * (ignorés si {@code null}) et combinables :
     * <ul>
     *   <li>{@code pattern} — motif LIKE (ex. {@code %tolkien%}), déjà en
     *       minuscules, comparé au titre OU à l'auteur (insensible à la casse)</li>
     *   <li>{@code status} — statut du roman</li>
     *   <li>{@code genreId} — présence du genre</li>
     * </ul>
     * Le tri (récent / A→Z) est porté par le {@link Pageable}.
     *
     * <p>Le motif est précalculé côté service (pas de {@code CONCAT} ici) pour
     * éviter que Postgres n'échoue à typer un paramètre {@code null}.
     */
    @Query("""
            SELECT n FROM Novel n
            WHERE (:pattern IS NULL OR LOWER(n.title) LIKE :pattern OR LOWER(n.author) LIKE :pattern)
              AND (:status IS NULL OR n.status = :status)
              AND (:genreId IS NULL OR EXISTS (SELECT 1 FROM n.genres g WHERE g.id = :genreId))
            """)
    Page<Novel> search(@Param("pattern") String pattern,
            @Param("status") NovelStatus status,
            @Param("genreId") Long genreId,
            Pageable pageable);

    /**
     * Même recherche/filtre, mais triée par POPULARITÉ (nombre d'ajouts en
     * bibliothèque, décroissant, puis les plus récents). Le tri est dans la
     * requête → le {@link Pageable} ne porte que la pagination.
     */
    @Query("""
            SELECT n FROM Novel n
            WHERE (:pattern IS NULL OR LOWER(n.title) LIKE :pattern OR LOWER(n.author) LIKE :pattern)
              AND (:status IS NULL OR n.status = :status)
              AND (:genreId IS NULL OR EXISTS (SELECT 1 FROM n.genres g WHERE g.id = :genreId))
            ORDER BY (SELECT COUNT(le) FROM LibraryEntry le WHERE le.novel = n) DESC, n.createdAt DESC
            """)
    Page<Novel> searchByPopularity(@Param("pattern") String pattern,
            @Param("status") NovelStatus status,
            @Param("genreId") Long genreId,
            Pageable pageable);

    /**
     * Même recherche/filtre, mais triée par NOTE MOYENNE décroissante.
     *
     * <p>Deux précautions dans le tri :
     * <ul>
     *   <li>{@code COALESCE(..., 0)} — sans lui, les romans sans aucun avis
     *       remonteraient en TÊTE sur PostgreSQL, où {@code NULL} passe avant
     *       en tri décroissant.</li>
     *   <li>le nombre d'avis départage les ex æquo : 5,0 sur trente avis passe
     *       devant 5,0 sur un seul, qui ne veut pas dire grand-chose.</li>
     * </ul>
     * Le tri est dans la requête → le {@link Pageable} ne porte que la pagination.
     */
    @Query("""
            SELECT n FROM Novel n
            WHERE (:pattern IS NULL OR LOWER(n.title) LIKE :pattern OR LOWER(n.author) LIKE :pattern)
              AND (:status IS NULL OR n.status = :status)
              AND (:genreId IS NULL OR EXISTS (SELECT 1 FROM n.genres g WHERE g.id = :genreId))
            ORDER BY COALESCE((SELECT AVG(r.rating) FROM Review r WHERE r.novel = n), 0) DESC,
                     (SELECT COUNT(r2) FROM Review r2 WHERE r2.novel = n) DESC,
                     n.createdAt DESC
            """)
    Page<Novel> searchByRating(@Param("pattern") String pattern,
            @Param("status") NovelStatus status,
            @Param("genreId") Long genreId,
            Pageable pageable);
}
