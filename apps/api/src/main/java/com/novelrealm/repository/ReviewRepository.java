package com.novelrealm.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.novelrealm.model.Review;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    /** L'avis d'un utilisateur sur un roman (au plus un, contrainte d'unicité). */
    Optional<Review> findByUser_IdAndNovel_Id(Long userId, Long novelId);

    /**
     * Les avis d'un roman, page par page. {@code @EntityGraph} charge l'auteur
     * dans la même requête : le mapping vers le DTO se fait hors transaction
     * (open-in-view désactivé), sans LazyInitializationException.
     */
    @EntityGraph(attributePaths = "user")
    Page<Review> findByNovel_Id(Long novelId, Pageable pageable);

    /**
     * Moyenne et nombre d'avis d'un roman, calculés par la base (une seule
     * requête, pas de chargement des lignes).
     */
    @Query("""
            select coalesce(avg(r.rating), 0) as average, count(r) as count
            from Review r
            where r.novel.id = :novelId
            """)
    RatingSummary summarizeByNovelId(@Param("novelId") Long novelId);

    /**
     * Nombre d'avis PAR note (1 à 5), pour l'histogramme de répartition.
     * Les notes sans aucun avis sont simplement absentes du résultat.
     */
    @Query("""
            select r.rating as rating, count(r) as count
            from Review r
            where r.novel.id = :novelId
            group by r.rating
            """)
    List<RatingBucket> countByRating(@Param("novelId") Long novelId);

    /** Projection du couple (moyenne, nombre) renvoyé par {@link #summarizeByNovelId}. */
    interface RatingSummary {
        double getAverage();

        long getCount();
    }

    /** Projection d'une barre de l'histogramme : une note et son nombre d'avis. */
    interface RatingBucket {
        int getRating();

        long getCount();
    }
}
