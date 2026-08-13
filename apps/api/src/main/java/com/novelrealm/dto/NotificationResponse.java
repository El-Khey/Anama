package com.novelrealm.dto;

import java.time.Instant;

import com.novelrealm.model.CommentMention;
import com.novelrealm.model.Notification;
import com.novelrealm.repository.NotificationRepository.NotificationView;

/**
 * Une ligne de la cloche (issue #45, §3). Tout ce qu'il faut pour l'afficher ET
 * pour y aller : {@code chapterId} ouvre le lecteur, {@code commentKind} dit si
 * la discussion est en fin de chapitre ou sur un passage, {@code blockIndex}
 * (passages) désigne le paragraphe tel qu'il s'affichait au moment de
 * l'événement.
 *
 * <p>Les champs d'acteur/roman/chapitre sont nullables : un compte supprimé ou
 * un roman retiré ne doit pas faire disparaître la notification, seulement la
 * rendre moins cliquable.
 */
public record NotificationResponse(
        Long id,
        Notification.Type type,
        Long actorId,
        String actorPseudo,
        String actorAvatarUrl,
        Long novelId,
        String novelTitle,
        String novelCoverUrl,
        Long chapterId,
        Integer chapterNumber,
        String chapterTitle,
        CommentMention.SourceKind commentKind,
        Long commentId,
        Integer blockIndex,
        String excerpt,
        boolean read,
        Instant createdAt
) {
    /** Projection du dépôt → DTO, champ à champ. */
    public static NotificationResponse from(NotificationView view) {
        return new NotificationResponse(
                view.getId(),
                view.getType(),
                view.getActorId(),
                view.getActorPseudo(),
                view.getActorAvatarUrl(),
                view.getNovelId(),
                view.getNovelTitle(),
                view.getNovelCoverUrl(),
                view.getChapterId(),
                view.getChapterNumber(),
                view.getChapterTitle(),
                view.getCommentKind(),
                view.getCommentId(),
                view.getBlockIndex(),
                view.getExcerpt(),
                view.isRead(),
                view.getCreatedAt());
    }
}
