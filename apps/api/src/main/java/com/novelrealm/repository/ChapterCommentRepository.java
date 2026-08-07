package com.novelrealm.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.novelrealm.model.ChapterComment;

public interface ChapterCommentRepository extends JpaRepository<ChapterComment, Long> {

    /**
     * Fils d'un chapitre (messages racines), les plus récents d'abord.
     *
     * <p>Un message supprimé n'est gardé QUE s'il porte encore des réponses
     * vivantes : sinon le fil afficherait des pierres tombales pour rien.
     * {@code @EntityGraph} charge l'auteur dans la même requête — le mapping vers
     * le DTO a lieu hors transaction (open-in-view désactivé).
     */
    @EntityGraph(attributePaths = "user")
    @Query(value = """
            select c from ChapterComment c
            where c.chapter.id = :chapterId
              and c.parent is null
              and (c.deletedAt is null
                   or exists (select r from ChapterComment r
                              where r.parent = c and r.deletedAt is null))
            order by c.createdAt desc
            """,
            countQuery = """
            select count(c) from ChapterComment c
            where c.chapter.id = :chapterId
              and c.parent is null
              and (c.deletedAt is null
                   or exists (select r from ChapterComment r
                              where r.parent = c and r.deletedAt is null))
            """)
    Page<ChapterComment> findRootsRecent(@Param("chapterId") Long chapterId, Pageable pageable);

    /**
     * Mêmes fils, mais les plus DISCUTÉS d'abord (nombre de réponses vivantes),
     * l'ancienneté départageant les ex aequo.
     *
     * <p>C'est le tri « là où ça parle ». Le tri « plus réagis » de l'issue #41
     * viendra quand les réactions existeront — il n'y a rien à compter avant.
     */
    @EntityGraph(attributePaths = "user")
    @Query(value = """
            select c from ChapterComment c
            where c.chapter.id = :chapterId
              and c.parent is null
              and (c.deletedAt is null
                   or exists (select r from ChapterComment r
                              where r.parent = c and r.deletedAt is null))
            order by (select count(r2) from ChapterComment r2
                      where r2.parent = c and r2.deletedAt is null) desc,
                     c.createdAt desc
            """,
            countQuery = """
            select count(c) from ChapterComment c
            where c.chapter.id = :chapterId
              and c.parent is null
              and (c.deletedAt is null
                   or exists (select r from ChapterComment r
                              where r.parent = c and r.deletedAt is null))
            """)
    Page<ChapterComment> findRootsDiscussed(@Param("chapterId") Long chapterId, Pageable pageable);

    /**
     * Réponses vivantes des fils affichés, en une seule requête pour toute la
     * page — surtout pas une requête par fil.
     */
    @EntityGraph(attributePaths = "user")
    @Query("""
            select c from ChapterComment c
            where c.parent.id in :parentIds
              and c.deletedAt is null
            order by c.createdAt asc
            """)
    List<ChapterComment> findRepliesOf(@Param("parentIds") Collection<Long> parentIds);

    /** Nombre de messages vivants d'un chapitre, réponses comprises. */
    long countByChapter_IdAndDeletedAtIsNull(Long chapterId);

    /**
     * Nombre de messages par chapitre pour tout un roman : c'est ce qui permet
     * d'afficher « où ça discute » dans la liste des chapitres, en un seul appel.
     * Les chapitres sans aucun message sont simplement absents du résultat.
     */
    @Query("""
            select c.chapter.id as chapterId, count(c) as count
            from ChapterComment c
            where c.chapter.novel.id = :novelId
              and c.deletedAt is null
            group by c.chapter.id
            """)
    List<ChapterCommentCount> countByNovel(@Param("novelId") Long novelId);

    /** Garde-fou anti-rafale : cet utilisateur a-t-il écrit depuis {@code since} ? */
    boolean existsByUser_IdAndCreatedAtAfter(Long userId, Instant since);

    /** Projection d'une ligne de {@link #countByNovel} : un chapitre et son total. */
    interface ChapterCommentCount {
        Long getChapterId();

        long getCount();
    }
}
