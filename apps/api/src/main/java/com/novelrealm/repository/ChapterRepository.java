package com.novelrealm.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.novelrealm.model.Chapter;
import com.novelrealm.dto.NovelChapterCount;
import java.util.Collection;
import java.util.List;

public interface ChapterRepository extends JpaRepository<Chapter, Long> {
    // Chapitres d'un roman, triés par numéro (relation directe novel → chapter).
    List<Chapter> findByNovelIdOrderByChapterNumber(Long novelId);

    // Nombre total de chapitres par roman (pour le résumé de progression).
    @Query("select new com.novelrealm.dto.NovelChapterCount(c.novel.id, count(c)) "
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
