package com.novelrealm.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
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

    /** Une annotation de cet utilisateur, pour la résoudre ou la supprimer. */
    Optional<PassageAnnotation> findByIdAndUser_Id(Long id, Long userId);

    // ── Activité d'un chapitre (#41, §4) ──────────────────────────────────────

    /**
     * Réactions du chapitre, comptées par bloc et par emoji, avec la mienne repérée.
     *
     * <p><b>Un décompte par emoji, pas une ligne par réaction.</b> Un chapitre
     * populaire porterait des milliers de réactions ; le lecteur, lui, n'a besoin que
     * de « ce bloc a 12 ❤️ ». L'agrégation se fait donc en base.
     *
     * <p>Le regroupement inclut {@code textHash} parce que deux annotations posées sur
     * le même index peuvent viser des textes différents — le chapitre a pu être
     * ré-ingéré entre les deux. C'est le couple (index, empreinte) qui identifie un
     * passage, pas l'index seul ; le service s'en sert pour résoudre les ancres.
     */
    @Query("""
            select a.blockIndex as blockIndex,
                   a.textHash   as textHash,
                   a.emoji      as emoji,
                   count(a)     as total,
                   sum(case when a.user.id = :userId then 1 else 0 end) as mine
            from PassageAnnotation a
            where a.chapter.id = :chapterId
              and a.kind = com.novelrealm.model.PassageAnnotation$Kind.REACTION
            group by a.blockIndex, a.textHash, a.emoji
            """)
    List<ReactionTally> tallyReactions(
            @Param("chapterId") Long chapterId,
            @Param("userId") Long userId);

    /** Nombre de commentaires par passage. Même regroupement, même raison. */
    @Query("""
            select a.blockIndex as blockIndex,
                   a.textHash   as textHash,
                   count(a)     as total
            from PassageAnnotation a
            where a.chapter.id = :chapterId
              and a.kind = com.novelrealm.model.PassageAnnotation$Kind.COMMENT
            group by a.blockIndex, a.textHash
            """)
    List<CommentTally> tallyComments(@Param("chapterId") Long chapterId);

    /**
     * Le fil d'un passage, du plus ancien au plus récent — une discussion se lit dans
     * l'ordre où elle s'est tenue.
     *
     * <p><b>Interrogé par empreinte, pas par index de bloc.</b> C'est la conséquence
     * directe du §2 : l'index se décale dès qu'un chapitre est ré-ingéré, et un fil
     * cherché à l'index reviendrait alors vide alors que les messages sont toujours
     * là. L'empreinte suit le texte. Le service écarte ensuite les rares homonymes —
     * deux blocs identiques dans un même chapitre — en résolvant chaque ancre.
     *
     * <p>Non paginé, volontairement : un fil accroché à une seule ligne de texte n'a
     * pas la taille d'un fil de fin de chapitre. Le jour où ce ne serait plus vrai,
     * c'est la signature qui changerait, pas l'écran.
     *
     * <p>{@code @EntityGraph} sur l'auteur : sans lui, afficher dix messages coûterait
     * dix requêtes de plus pour dix pseudos.
     */
    @EntityGraph(attributePaths = "user")
    @Query("""
            select a from PassageAnnotation a
            where a.chapter.id = :chapterId
              and a.textHash = :textHash
              and a.kind = com.novelrealm.model.PassageAnnotation$Kind.COMMENT
            order by a.createdAt asc
            """)
    List<PassageAnnotation> findThread(
            @Param("chapterId") Long chapterId,
            @Param("textHash") String textHash);

    /**
     * Ma réaction sur ce passage, s'il y en a une — il ne peut y en avoir qu'une, la
     * base le garantit. Repérée par empreinte pour la même raison que le fil.
     */
    @Query("""
            select a from PassageAnnotation a
            where a.chapter.id = :chapterId
              and a.textHash = :textHash
              and a.user.id = :userId
              and a.kind = com.novelrealm.model.PassageAnnotation$Kind.REACTION
            """)
    Optional<PassageAnnotation> findMyReaction(
            @Param("chapterId") Long chapterId,
            @Param("textHash") String textHash,
            @Param("userId") Long userId);

    /** Anti-rafale : ai-je écrit un commentaire de passage tout récemment ? */
    boolean existsByUser_IdAndKindAndCreatedAtAfter(
            Long userId, PassageAnnotation.Kind kind, Instant since);

    /** Les identifiants des réponses d'un fil — pour purger leurs mentions avant cascade. */
    @Query("select a.id from PassageAnnotation a where a.parent.id = :rootId")
    List<Long> findReplyIds(@Param("rootId") Long rootId);

    // ── « Mes commentaires » (issue #45, §4) ─────────────────────────────────

    /**
     * MES commentaires de passage, les plus récents d'abord — même stratégie de
     * projection que {@link #findQuotes} et pour la même raison : surtout ne pas
     * embarquer le texte des chapitres. L'extrait du passage commenté est résolu
     * ensuite par le service, chapitre par chapitre distinct, pas ligne par ligne.
     *
     * <p>{@code left join a.parent} et non une navigation implicite
     * {@code a.parent.id} : l'implicite produirait un INNER join et ferait
     * disparaître tous les messages racines (parent null) du résultat.
     */
    @Query("""
            select a.id            as id,
                   a.body          as body,
                   a.gifUrl        as gifUrl,
                   a.gifPreviewUrl as gifPreviewUrl,
                   a.isSpoiler     as spoiler,
                   a.createdAt     as createdAt,
                   p.id            as parentId,
                   a.blockIndex    as blockIndex,
                   a.textHash      as textHash,
                   c.id            as chapterId,
                   c.chapterNumber as chapterNumber,
                   c.title         as chapterTitle,
                   n.id            as novelId,
                   n.title         as novelTitle,
                   n.coverImageUrl as novelCoverUrl
            from PassageAnnotation a
              left join a.parent p
              join a.chapter c
              join c.novel n
            where a.user.id = :userId
              and a.kind = com.novelrealm.model.PassageAnnotation$Kind.COMMENT
            order by a.createdAt desc
            """)
    List<MyPassageCommentView> findMyComments(@Param("userId") Long userId, Pageable pageable);

    /** Total de MES commentaires de passage (pagination du flux fusionné). */
    long countByUser_IdAndKind(Long userId, PassageAnnotation.Kind kind);

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

    /** Un emoji, sur un passage, et combien de fois — dont moi ou non. */
    interface ReactionTally {
        int getBlockIndex();

        String getTextHash();

        String getEmoji();

        long getTotal();

        /** 1 si j'ai posé cet emoji, 0 sinon — l'unicité en base garantit ces deux cas. */
        long getMine();
    }

    /** Combien de commentaires sur un passage. */
    interface CommentTally {
        int getBlockIndex();

        String getTextHash();

        long getTotal();
    }

    /** Une ligne de « Mes commentaires » côté passage — voir {@link #findMyComments}. */
    interface MyPassageCommentView {
        Long getId();

        String getBody();

        String getGifUrl();

        String getGifPreviewUrl();

        boolean getSpoiler();

        Instant getCreatedAt();

        Long getParentId();

        int getBlockIndex();

        String getTextHash();

        Long getChapterId();

        int getChapterNumber();

        String getChapterTitle();

        Long getNovelId();

        String getNovelTitle();

        String getNovelCoverUrl();
    }
}
