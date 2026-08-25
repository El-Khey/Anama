package com.novelrealm.repository;

import java.time.Instant;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.novelrealm.model.CommentMention;
import com.novelrealm.model.Notification;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    /**
     * La cloche d'un utilisateur, la plus récente en tête.
     *
     * <p><b>Projection scalaire et non entités</b>, pour la même raison que
     * {@code PassageAnnotationRepository.QuoteView} : charger l'entité
     * {@code Chapter} embarquerait sa colonne {@code content} — le texte intégral
     * du chapitre — pour afficher un titre. Les jointures sont {@code left} :
     * l'acteur, le roman ou le chapitre d'une notification peuvent avoir disparu
     * (compte supprimé, roman retiré), la notification doit rester lisible.
     */
    @Query(value = """
            select x.id             as id,
                   x.type           as type,
                   a.id             as actorId,
                   a.pseudo         as actorPseudo,
                   a.avatarUrl      as actorAvatarUrl,
                   n.id             as novelId,
                   n.title          as novelTitle,
                   n.coverImageUrl  as novelCoverUrl,
                   c.id             as chapterId,
                   c.chapterNumber  as chapterNumber,
                   c.title          as chapterTitle,
                   x.commentKind    as commentKind,
                   x.commentId      as commentId,
                   x.blockIndex     as blockIndex,
                   x.excerpt        as excerpt,
                   x.read           as read,
                   x.createdAt      as createdAt
            from Notification x
              left join x.actor a
              left join x.novel n
              left join x.chapter c
            where x.user.id = :userId
              and (:unreadOnly = false or x.read = false)
            order by x.createdAt desc
            """,
            countQuery = """
            select count(x) from Notification x
            where x.user.id = :userId
              and (:unreadOnly = false or x.read = false)
            """)
    Page<NotificationView> findForUser(
            @Param("userId") Long userId,
            @Param("unreadOnly") boolean unreadOnly,
            Pageable pageable);

    /** Le badge de la cloche : combien de non-lues. */
    long countByUser_IdAndReadFalse(Long userId);

    /** Une notification qui m'appartient — garde-fou du « marquer comme lue ». */
    Optional<Notification> findByIdAndUser_Id(Long id, Long userId);

    /**
     * « Tout marquer comme lu » en une requête UPDATE — pas un SELECT de toutes
     * les lignes suivi de N sauvegardes. {@code clearAutomatically} évite qu'un
     * cache de persistance périmé ne renvoie ensuite des lignes encore « non
     * lues ».
     */
    @Modifying(clearAutomatically = true)
    @Query("update Notification x set x.read = true where x.user.id = :userId and x.read = false")
    int markAllRead(@Param("userId") Long userId);

    /** Une ligne de la cloche — voir {@link #findForUser}. */
    interface NotificationView {
        Long getId();

        Notification.Type getType();

        Long getActorId();

        String getActorPseudo();

        String getActorAvatarUrl();

        Long getNovelId();

        String getNovelTitle();

        String getNovelCoverUrl();

        Long getChapterId();

        Integer getChapterNumber();

        String getChapterTitle();

        CommentMention.SourceKind getCommentKind();

        Long getCommentId();

        Integer getBlockIndex();

        String getExcerpt();

        boolean isRead();

        Instant getCreatedAt();
    }
}
