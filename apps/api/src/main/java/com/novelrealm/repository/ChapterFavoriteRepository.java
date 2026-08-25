package com.novelrealm.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.novelrealm.model.ChapterFavorite;
import com.novelrealm.model.ChapterFavoriteId;

/**
 * Accès aux favoris de chapitre.
 *
 * <p>Les requêtes « traversent » les relations {@code @ManyToOne} : {@code User_Id}
 * = {@code user.id}, {@code Chapter_Id} = {@code chapter.id}, et
 * {@code Chapter_Novel_Id} = {@code chapter.novel.id} (favoris d'un roman). Le
 * chapitre et son roman sont chargés en même temps ({@code @EntityGraph}) car on
 * les lit hors transaction, dans le controller, pour construire le DTO.
 */
public interface ChapterFavoriteRepository extends JpaRepository<ChapterFavorite, ChapterFavoriteId> {

    /** Le favori d'un utilisateur sur un chapitre précis, s'il existe. */
    Optional<ChapterFavorite> findByUser_IdAndChapter_Id(Long userId, Long chapterId);

    /** Le chapitre est-il déjà en favori pour cet utilisateur ? */
    boolean existsByUser_IdAndChapter_Id(Long userId, Long chapterId);

    /** Nombre de chapitres favoris de l'utilisateur (stats du profil). */
    long countByUser_Id(Long userId);

    /** Tous les favoris de l'utilisateur sur les chapitres d'un roman, plus récents en tête. */
    @EntityGraph(attributePaths = {"chapter", "chapter.novel"})
    List<ChapterFavorite> findByUser_IdAndChapter_Novel_IdOrderByFavoritedAtDesc(Long userId, Long novelId);

    /** Tous les favoris de l'utilisateur (tous romans confondus), plus récents en tête. */
    @EntityGraph(attributePaths = {"chapter", "chapter.novel"})
    List<ChapterFavorite> findByUser_IdOrderByFavoritedAtDesc(Long userId);
}
