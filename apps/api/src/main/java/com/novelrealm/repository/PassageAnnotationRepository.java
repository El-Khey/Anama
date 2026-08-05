package com.novelrealm.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.novelrealm.model.PassageAnnotation;

public interface PassageAnnotationRepository extends JpaRepository<PassageAnnotation, Long> {

    /**
     * Les citations d'un utilisateur, avec de quoi les afficher (roman + chapitre).
     *
     * <p><b>Projection et non entités, volontairement.</b> Charger l'entité
     * {@code Chapter} embarquerait sa colonne {@code content} — le texte intégral du
     * chapitre. Une page de 20 citations, c'est 20 chapitres entiers rapatriés pour
     * afficher 20 phrases. La projection ne sélectionne que les colonnes utiles.
     *
     * <p>{@code novelId} et {@code pattern} sont facultatifs : passer {@code null}
     * désactive le filtre correspondant. {@code since} ne l'est pas : le service passe
     * {@link java.time.Instant#EPOCH} quand aucune période n'est demandée, plutôt qu'un
     * {@code null}. Un paramètre nul non typé est exactement ce qui a produit le bug
     * {@code lower(bytea)} décrit plus bas ; ne jamais en lier évite la question.
     *
     * <p><b>{@code pattern} arrive déjà en minuscules et déjà entouré de {@code %}</b>
     * (voir {@code QuoteService#likePattern}). Construire le motif dans la requête —
     * {@code lower(concat('%', :search, '%'))} — plantait dès que le paramètre était
     * nul : PostgreSQL ne peut pas deviner le type d'un {@code null} non typé, le
     * prend pour du {@code bytea}, et {@code lower(bytea)} n'existe pas. Comparé
     * directement à une colonne texte via {@code like}, le type s'infère tout seul.
     *
     * <p><b>Aucun {@code order by} ici : c'est le {@link Pageable} qui l'apporte.</b>
     * L'écran propose « plus récentes » ou « plus anciennes » ; un tri figé dans la
     * requête serait concaténé avec celui du {@code Pageable} et le premier gagnerait,
     * rendant le second inopérant. Le service a la charge de toujours fournir un tri —
     * sans lui, la pagination n'aurait aucun ordre garanti.
     */
    @Query(value = """
            select a.id                as id,
                   a.quotedText        as quotedText,
                   a.createdAt         as createdAt,
                   c.id                as chapterId,
                   c.chapterNumber     as chapterNumber,
                   c.title             as chapterTitle,
                   n.id                as novelId,
                   n.title             as novelTitle,
                   n.coverImageUrl     as novelCoverUrl
            from PassageAnnotation a
              join a.chapter c
              join c.novel n
            where a.user.id = :userId
              and a.kind = com.novelrealm.model.PassageAnnotation$Kind.QUOTE
              and a.createdAt >= :since
              and (:novelId is null or n.id = :novelId)
              and (:pattern is null or lower(a.quotedText) like :pattern escape '!')
            """,
            countQuery = """
            select count(a)
            from PassageAnnotation a
              join a.chapter c
              join c.novel n
            where a.user.id = :userId
              and a.kind = com.novelrealm.model.PassageAnnotation$Kind.QUOTE
              and a.createdAt >= :since
              and (:novelId is null or n.id = :novelId)
              and (:pattern is null or lower(a.quotedText) like :pattern escape '!')
            """)
    Page<QuoteView> findQuotes(
            @Param("userId") Long userId,
            @Param("novelId") Long novelId,
            @Param("pattern") String pattern,
            @Param("since") Instant since,
            Pageable pageable);

    /**
     * Nombre de citations par roman pour un utilisateur — alimente à la fois les
     * filtres de la page « Mes citations » et le compteur de la fiche d'un roman,
     * en un seul appel.
     */
    @Query("""
            select n.id as novelId, n.title as novelTitle, count(a) as count
            from PassageAnnotation a
              join a.chapter c
              join c.novel n
            where a.user.id = :userId
              and a.kind = com.novelrealm.model.PassageAnnotation$Kind.QUOTE
            group by n.id, n.title
            order by count(a) desc
            """)
    List<NovelQuoteCount> countQuotesByNovel(@Param("userId") Long userId);

    /** Une citation de cet utilisateur, pour la résoudre ou la supprimer. */
    Optional<PassageAnnotation> findByIdAndUser_Id(Long id, Long userId);

    /** Projection d'une carte de la page « Mes citations » (sans le texte du chapitre). */
    interface QuoteView {
        Long getId();

        String getQuotedText();

        Instant getCreatedAt();

        Long getChapterId();

        int getChapterNumber();

        String getChapterTitle();

        Long getNovelId();

        String getNovelTitle();

        String getNovelCoverUrl();
    }

    /** Projection d'une ligne de {@link #countQuotesByNovel}. */
    interface NovelQuoteCount {
        Long getNovelId();

        String getNovelTitle();

        long getCount();
    }
}
