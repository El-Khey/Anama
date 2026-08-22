package com.novelrealm.controller;

import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.novelrealm.dto.ChapterCommentResponse;
import com.novelrealm.dto.CommentCountResponse;
import com.novelrealm.dto.CommentReactionsResponse;
import com.novelrealm.dto.CreateChapterCommentRequest;
import com.novelrealm.dto.PageResponse;
import com.novelrealm.dto.ReactToCommentRequest;
import com.novelrealm.dto.UpdateChapterCommentRequest;
import com.novelrealm.service.ChapterCommentService;
import com.novelrealm.service.CommentReactionService;

import jakarta.validation.Valid;

/**
 * API des messages de fin de chapitre (#41). L'auteur est toujours l'utilisateur
 * connecté : aucun identifiant d'utilisateur ne transite par l'URL, donc personne
 * ne peut écrire ni supprimer au nom d'un autre.
 *
 * <p>Les chemins ne partagent pas tous le même préfixe — un message se lit sous
 * son chapitre mais se modifie par son propre identifiant, sans avoir à rappeler
 * de quel chapitre il vient.
 */
@RestController
@RequestMapping("/api")
public class ChapterCommentController {

    private final ChapterCommentService commentService;
    private final CommentReactionService reactionService;

    public ChapterCommentController(
            ChapterCommentService commentService,
            CommentReactionService reactionService) {
        this.commentService = commentService;
        this.reactionService = reactionService;
    }

    /**
     * GET /api/chapters/{chapterId}/comments?sort=recent|discussed&page=&size=
     * — les fils du chapitre, réponses comprises.
     */
    @GetMapping("/chapters/{chapterId}/comments")
    public ResponseEntity<PageResponse<ChapterCommentResponse>> list(
            @PathVariable Long chapterId,
            @RequestParam(required = false) String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            Authentication authentication) {
        Page<ChapterCommentResponse> body = commentService.listThreads(
                emailOf(authentication),
                chapterId,
                ChapterCommentService.Sort.parse(sort),
                page,
                size);
        return ResponseEntity.ok(PageResponse.from(body));
    }

    /**
     * GET /api/chapters/{chapterId}/comments/count — juste le nombre.
     *
     * <p>Séparé de la liste à dessein : le lecteur affiche « 42 commentaires »
     * sous « Fin du chapitre » sans charger un seul message.
     */
    @GetMapping("/chapters/{chapterId}/comments/count")
    public ResponseEntity<CommentCountResponse> count(@PathVariable Long chapterId) {
        return ResponseEntity.ok(new CommentCountResponse(commentService.count(chapterId)));
    }

    /**
     * GET /api/novels/{novelId}/comment-counts — nombre de messages par chapitre,
     * pour signaler « où ça discute » dans la liste des chapitres d'un roman.
     * Les chapitres sans message sont absents de la réponse.
     */
    @GetMapping("/novels/{novelId}/comment-counts")
    public ResponseEntity<Map<Long, Long>> countsByNovel(@PathVariable Long novelId) {
        return ResponseEntity.ok(commentService.countsByNovel(novelId));
    }

    /** POST /api/chapters/{chapterId}/comments — publie un message ou une réponse. */
    @PostMapping("/chapters/{chapterId}/comments")
    public ResponseEntity<ChapterCommentResponse> create(
            @PathVariable Long chapterId,
            @Valid @RequestBody CreateChapterCommentRequest request,
            Authentication authentication) {
        ChapterCommentResponse created = commentService.create(
                authentication.getName(), chapterId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /** PATCH /api/comments/{commentId} — modifie SON message. */
    @PatchMapping("/comments/{commentId}")
    public ResponseEntity<ChapterCommentResponse> update(
            @PathVariable Long commentId,
            @Valid @RequestBody UpdateChapterCommentRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(
                commentService.update(authentication.getName(), commentId, request.body()));
    }

    /**
     * PUT /api/comments/{commentId}/reactions — pose ou retire une réaction emoji.
     *
     * <p>{@code PUT} et non {@code POST} : le geste est idempotent au sens où l'état
     * final ne dépend que de l'emoji envoyé et de l'état courant (présent → retiré,
     * absent → posé), jamais du nombre d'appels. Un double tap ne double pas la
     * réaction. Réagir à son propre message est permis, comme sur Discord.
     */
    @PutMapping("/comments/{commentId}/reactions")
    public ResponseEntity<CommentReactionsResponse> react(
            @PathVariable Long commentId,
            @Valid @RequestBody ReactToCommentRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(reactionService.toggleOnChapterComment(
                authentication.getName(), commentId, request.emoji()));
    }

    /** DELETE /api/comments/{commentId} — retire SON message (204). */
    @DeleteMapping("/comments/{commentId}")
    public ResponseEntity<Void> delete(
            @PathVariable Long commentId,
            Authentication authentication) {
        commentService.delete(authentication.getName(), commentId);
        return ResponseEntity.noContent().build();
    }

    private static String emailOf(Authentication authentication) {
        return authentication == null ? null : authentication.getName();
    }
}
