package com.novelrealm.service.notification;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.novelrealm.dto.notification.NotificationResponse;
import com.novelrealm.exception.notification.NotificationNotFoundException;
import com.novelrealm.model.Chapter;
import com.novelrealm.model.CommentMention;
import com.novelrealm.model.Notification;
import com.novelrealm.model.User;
import com.novelrealm.repository.NotificationRepository;
import com.novelrealm.service.user.UserService;

/**
 * Notifications dans l'app (issue #45, §3) : réponses à mes commentaires et
 * mentions. L'issue #22 (nouveaux chapitres) se greffera ici — même table, mêmes
 * endpoints, même cloche.
 *
 * <p>Les méthodes {@code notify*} sont appelées par les services de commentaires
 * <b>dans leur transaction</b> : un message publié sans sa notification (ou
 * l'inverse) laisserait la cloche mentir. Elles ne notifient jamais l'auteur de
 * son propre geste.
 */
@Service
public class NotificationService {

    private static final int MAX_PAGE_SIZE = 50;

    private final NotificationRepository notificationRepository;
    private final UserService userService;

    public NotificationService(
            NotificationRepository notificationRepository,
            UserService userService) {
        this.notificationRepository = notificationRepository;
        this.userService = userService;
    }

    // ── Déclencheurs (appelés par les services de commentaires) ───────────────

    /** « {actor} a répondu à votre commentaire ». Silencieux si on se répond à soi-même. */
    @Transactional
    public void notifyReply(
            User recipient,
            User actor,
            Chapter chapter,
            CommentMention.SourceKind commentKind,
            Long commentId,
            Integer blockIndex,
            String body) {
        if (recipient.getId().equals(actor.getId())) {
            return;
        }
        notificationRepository.save(Notification.reply(
                recipient, actor, chapter, commentKind, commentId, blockIndex, excerptOf(body)));
    }

    /** « {actor} vous a mentionné ». Silencieux si on se mentionne soi-même. */
    @Transactional
    public void notifyMention(
            User recipient,
            User actor,
            Chapter chapter,
            CommentMention.SourceKind commentKind,
            Long commentId,
            Integer blockIndex,
            String body) {
        if (recipient.getId().equals(actor.getId())) {
            return;
        }
        notificationRepository.save(Notification.mention(
                recipient, actor, chapter, commentKind, commentId, blockIndex, excerptOf(body)));
    }

    // ── Lecture / actions (endpoints) ─────────────────────────────────────────

    /** La cloche, paginée, la plus récente en tête. */
    @Transactional(readOnly = true)
    public Page<NotificationResponse> list(String email, boolean unreadOnly, int page, int size) {
        User user = userService.findByEmail(email);
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, MAX_PAGE_SIZE));
        return notificationRepository.findForUser(user.getId(), unreadOnly, pageable)
                .map(NotificationResponse::from);
    }

    /** Le badge : combien de non-lues. */
    @Transactional(readOnly = true)
    public long unreadCount(String email) {
        User user = userService.findByEmail(email);
        return notificationRepository.countByUser_IdAndReadFalse(user.getId());
    }

    /** Marque UNE notification comme lue. 404 si elle n'est pas à moi. */
    @Transactional
    public void markRead(String email, Long notificationId) {
        User user = userService.findByEmail(email);
        Notification notification = notificationRepository
                .findByIdAndUser_Id(notificationId, user.getId())
                .orElseThrow(() -> new NotificationNotFoundException(notificationId));
        notification.markRead();
    }

    /** Éteint toute la cloche d'un coup. */
    @Transactional
    public void markAllRead(String email) {
        User user = userService.findByEmail(email);
        notificationRepository.markAllRead(user.getId());
    }

    // ── Interne ───────────────────────────────────────────────────────────────

    /**
     * L'extrait figé dans la notification. Un message sans texte est forcément un
     * GIF seul — « GIF » dit alors l'essentiel.
     */
    private static String excerptOf(String body) {
        String cleaned = body == null ? "" : body.strip();
        if (cleaned.isEmpty()) {
            return "GIF";
        }
        if (cleaned.length() <= Notification.MAX_EXCERPT_LENGTH) {
            return cleaned;
        }
        return cleaned.substring(0, Notification.MAX_EXCERPT_LENGTH - 1) + "…";
    }
}
