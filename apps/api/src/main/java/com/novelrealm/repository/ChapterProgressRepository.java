package com.novelrealm.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.novelrealm.dto.chapter.NovelChapterCount;
import com.novelrealm.model.ChapterProgress;
import com.novelrealm.model.ChapterProgressId;

/**
 * Accès à la progression de lecture.
 *
 * <p>Les requêtes « traversent » les relations {@code @ManyToOne} : {@code User_Id}
 * = {@code user.id}, {@code Chapter_Id} = {@code chapter.id}, et
 * {@code Chapter_Novel_Id} = {@code chapter.novel.id} (progression de tous les
 * chapitres d'un roman).
 */
public interface ChapterProgressRepository extends JpaRepository<ChapterProgress, ChapterProgressId> {

    /** La progression d'un utilisateur sur un chapitre précis, si elle existe. */
    Optional<ChapterProgress> findByUser_IdAndChapter_Id(Long userId, Long chapterId);

    /** La progression existante d'un utilisateur sur un lot de chapitres (marquage en masse). */
    List<ChapterProgress> findByUser_IdAndChapter_IdIn(Long userId, Collection<Long> chapterIds);

    /** Nombre total de chapitres lus par l'utilisateur (stats du profil). */
    long countByUser_IdAndReadTrue(Long userId);

    /**
     * Jours calendaires d'activité de lecture (dates distinctes de
     * {@code read_at}), pour les statistiques du profil (jours de lecture,
     * séries). Approximation assumée : {@code read_at} porte la DERNIÈRE
     * activité de chaque chapitre.
     */
    @Query(value = "SELECT DISTINCT CAST(read_at AS date) FROM chapter_progress WHERE user_id = :userId",
            nativeQuery = true)
    List<java.time.LocalDate> findActivityDates(@Param("userId") Long userId);

    /** Toute la progression d'un utilisateur sur les chapitres d'un roman. */
    List<ChapterProgress> findByUser_IdAndChapter_Novel_Id(Long userId, Long novelId);

    /** Nombre de chapitres LUS par roman, pour un utilisateur (résumé de progression). */
    @Query("select new com.novelrealm.dto.chapter.NovelChapterCount(cp.chapter.novel.id, count(cp)) "
            + "from ChapterProgress cp where cp.user.id = :userId and cp.read = true "
            + "group by cp.chapter.novel.id")
    List<NovelChapterCount> countReadPerNovel(@Param("userId") Long userId);

    // ---- Historique de lecture (issue #15) ----------------------------------
    // Une entrée d'historique = une ligne de progression (chapitre lu ou entamé).
    // Le chapitre ET son roman sont chargés en même temps (@EntityGraph) car on
    // les lit hors transaction, dans le controller, pour construire le DTO.

    /** Page d'historique de l'utilisateur (tous romans confondus). Tri via {@link Pageable}. */
    @EntityGraph(attributePaths = {"chapter", "chapter.novel"})
    Page<ChapterProgress> findByUser_Id(Long userId, Pageable pageable);

    /** Page d'historique de l'utilisateur restreinte à un roman. Tri via {@link Pageable}. */
    @EntityGraph(attributePaths = {"chapter", "chapter.novel"})
    Page<ChapterProgress> findByUser_IdAndChapter_Novel_Id(Long userId, Long novelId, Pageable pageable);

    /** Purge tout l'historique de l'utilisateur. Renvoie le nombre de lignes supprimées. */
    @Modifying
    @Query("delete from ChapterProgress cp where cp.user.id = :userId")
    int deleteByUser_Id(@Param("userId") Long userId);

    /**
     * Purge l'historique d'un roman pour l'utilisateur. On passe par une
     * sous-requête sur les chapitres du roman : un {@code DELETE} en masse ne
     * peut pas « traverser » {@code cp.chapter.novel} par une jointure.
     */
    @Modifying
    @Query("delete from ChapterProgress cp where cp.user.id = :userId "
            + "and cp.chapter.id in (select c.id from Chapter c where c.novel.id = :novelId)")
    int deleteByUser_IdAndNovel(@Param("userId") Long userId, @Param("novelId") Long novelId);
}
