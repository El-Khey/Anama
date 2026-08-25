package com.novelrealm.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.novelrealm.dto.common.PageResponse;
import com.novelrealm.dto.notification.NotificationResponse;
import com.novelrealm.dto.notification.UnreadCountResponse;
import com.novelrealm.service.notification.NotificationService;

/**
 * La cloche (issue #45, §3). Toujours celle de l'utilisateur connecté — aucun
 * identifiant d'utilisateur dans l'URL, personne ne lit la cloche d'un autre.
 *
 * <p>{@code unread-count} est une route à part, exprès : c'est elle que l'app
 * appelle à chaque retour au premier plan pour le badge — elle doit coûter un
 * COUNT, pas une page entière.
 */
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    /** GET /api/notifications?unreadOnly=&page=&size= — la cloche, paginée. */
    @GetMapping
    public ResponseEntity<PageResponse<NotificationResponse>> list(
            @RequestParam(defaultValue = "false") boolean unreadOnly,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication) {
        return ResponseEntity.ok(PageResponse.from(
                notificationService.list(authentication.getName(), unreadOnly, page, size)));
    }

    /** GET /api/notifications/unread-count — le badge. */
    @GetMapping("/unread-count")
    public ResponseEntity<UnreadCountResponse> unreadCount(Authentication authentication) {
        return ResponseEntity.ok(
                new UnreadCountResponse(notificationService.unreadCount(authentication.getName())));
    }

    /** POST /api/notifications/{id}/read — marque UNE notification comme lue (204). */
    @PostMapping("/{id}/read")
    public ResponseEntity<Void> markRead(
            @PathVariable Long id, Authentication authentication) {
        notificationService.markRead(authentication.getName(), id);
        return ResponseEntity.noContent().build();
    }

    /** POST /api/notifications/read-all — éteint toute la cloche (204). */
    @PostMapping("/read-all")
    public ResponseEntity<Void> markAllRead(Authentication authentication) {
        notificationService.markAllRead(authentication.getName());
        return ResponseEntity.noContent().build();
    }
}
