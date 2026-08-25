package com.novelrealm.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.novelrealm.dto.comment.MyCommentResponse;
import com.novelrealm.dto.common.PageResponse;
import com.novelrealm.service.comment.MyCommentsService;

/**
 * « Mes commentaires » (issue #45, §4) : le flux unifié de tout ce que
 * l'utilisateur connecté a écrit — fin de chapitre et passages confondus.
 *
 * <p>Pas de route de suppression ici : chaque message se supprime par sa route
 * existante ({@code DELETE /api/comments/{id}} ou
 * {@code DELETE /api/passages/annotations/{id}}), le champ {@code kind} de la
 * réponse dit laquelle utiliser.
 */
@RestController
@RequestMapping("/api/users/me/comments")
public class MyCommentsController {

    private final MyCommentsService myCommentsService;

    public MyCommentsController(MyCommentsService myCommentsService) {
        this.myCommentsService = myCommentsService;
    }

    /** GET /api/users/me/comments?page=&size= — les plus récents d'abord. */
    @GetMapping
    public ResponseEntity<PageResponse<MyCommentResponse>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication) {
        return ResponseEntity.ok(myCommentsService.list(authentication.getName(), page, size));
    }
}
