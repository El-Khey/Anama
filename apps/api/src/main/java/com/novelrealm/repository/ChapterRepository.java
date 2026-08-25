package com.novelrealm.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Collection;
import java.util.List;

import com.novelrealm.dto.chapter.NovelChapterCount;
import com.novelrealm.model.Chapter;

public interface ChapterRepository extends JpaRepository<Chapter, Long> {
    // Chapitres d'un roman, triés par numéro (relation directe novel → chapter).
    List<Chapter> findByNovelIdOrderByChapterNumber(Long novelId);

    // ── Ingestion V2 ──────────────────────────────────────────────────────────
    // Plus grand numéro déjà stocké pour un roman (0 si aucun) : borne basse pour
    // ne tirer que les chapitres nouveaux lors de la synchro incrémentale.
    @Query("select coalesce(max(c.chapterNumber), 0) from Chapter c where c.novel.id = :novelId")
    int findMaxChapterNumber(@Param("novelId") Long novelId);

    // Ce chapitre est-il déjà en base ? (garde d'idempotence par chapitre.)
    boolean existsByNovelIdAndChapterNumber(Long novelId, Integer chapterNumber);

    // Les seuls NUMÉROS déjà stockés d'un roman — projection légère pour bâtir en
    // une requête l'ensemble des chapitres existants sans charger les entités
    // (ni surtout leur `content`, potentiellement énorme).
    @Query("select c.chapterNumber from Chapter c where c.novel.id = :novelId")
    List<Integer> findChapterNumbersByNovelId(@Param("novelId") Long novelId);

    // Nombre total de chapitres par roman (pour le résumé de progression).
    @Query("select new com.novelrealm.dto.chapter.NovelChapterCount(c.novel.id, count(c)) "
            + "from Chapter c group by c.novel.id")
    List<NovelChapterCount> countChaptersPerNovel();

    /**
     * Le texte de quelques chapitres précis, en une requête — pour résoudre les
     * extraits de « Mes commentaires » (issue #45, §4). Projection : on veut le
     * contenu de CES chapitres-là, pas des entités complètes dans le cache de
     * persistance.
     */
    @Query("select c.id as id, c.content as content from Chapter c where c.id in :ids")
    List<ChapterContentView> findContentByIdIn(@Param("ids") Collection<Long> ids);

    /** Un chapitre réduit à son texte — voir {@link #findContentByIdIn}. */
    interface ChapterContentView {
        Long getId();

        String getContent();
    }
}
