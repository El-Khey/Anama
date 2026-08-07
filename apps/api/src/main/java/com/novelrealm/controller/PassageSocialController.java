package com.novelrealm.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.novelrealm.dto.ChapterActivityResponse;
import com.novelrealm.dto.CreatePassageCommentRequest;
import com.novelrealm.dto.PassageCommentResponse;
import com.novelrealm.dto.PassageReactionResponse;
import com.novelrealm.dto.ReactToPassageRequest;
import com.novelrealm.service.PassageSocialService;

import jakarta.validation.Valid;

/**
 * Réactions et commentaires accrochés à un passage (#41, §4).
 *
 * <p><b>Deux endpoints de lecture, et c'est voulu.</b> Les agrégats couvrent tout le
 * chapitre en un appel — c'est ce que le lecteur voit en défilant. Le fil complet est
 * une seconde route, parce qu'il n'est chargé que si quelqu'un ouvre vraiment une
 * discussion : la plupart des blocs actifs ne seront jamais dépliés.
 *
 * <p>Les index de bloc échangés ici sont ceux de la version <b>actuelle</b> du
 * chapitre — ceux que l'app affiche. Le recalage des ancres est entièrement à la
 * charge du service.
 */
@RestController
@RequestMapping("/api")
public class PassageSocialController {

    private final PassageSocialService passageService;

    public PassageSocialController(PassageSocialService passageService) {
        this.passageService = passageService;
    }

    /**
     * GET /api/chapters/{chapterId}/activity — l'activité de tous les blocs, en un appel.
     *
     * <p>C'est l'appel qui alimente les marques en marge du lecteur. Un appel par bloc
     * serait intenable sur un chapitre de cinquante paragraphes.
     */
    @GetMapping("/chapters/{chapterId}/activity")
    public ResponseEntity<ChapterActivityResponse> activity(
            @PathVariable Long chapterId,
            Authentication authentication) {
        return ResponseEntity.ok(passageService.activity(authentication.getName(), chapterId));
    }

    /** GET /api/chapters/{chapterId}/passages/{block}/comments — le fil d'un passage. */
    @GetMapping("/chapters/{chapterId}/passages/{block}/comments")
    public ResponseEntity<List<PassageCommentResponse>> thread(
            @PathVariable Long chapterId,
            @PathVariable("block") int blockIndex,
            Authentication authentication) {
        return ResponseEntity.ok(
                passageService.thread(authentication.getName(), chapterId, blockIndex));
    }

    /** POST /api/chapters/{chapterId}/passages/comments — accroche un message à un bloc. */
    @PostMapping("/chapters/{chapterId}/passages/comments")
    public ResponseEntity<PassageCommentResponse> comment(
            @PathVariable Long chapterId,
            @Valid @RequestBody CreatePassageCommentRequest request,
            Authentication authentication) {
        PassageCommentResponse created = passageService.comment(
                authentication.getName(),
                chapterId,
                request.blockIndex(),
                request.body(),
                request.isSpoiler(),
                request.parentId());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * PUT /api/chapters/{chapterId}/passages/reactions — pose, remplace ou retire.
     *
     * <p>{@code PUT} et non {@code POST} : le geste est idempotent au sens où l'état
     * final ne dépend que de l'emoji envoyé et de l'état courant, jamais du nombre
     * d'appels. Un double tap accidentel ne crée pas deux réactions.
     */
    @PutMapping("/chapters/{chapterId}/passages/reactions")
    public ResponseEntity<PassageReactionResponse> react(
            @PathVariable Long chapterId,
            @Valid @RequestBody ReactToPassageRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(passageService.react(
                authentication.getName(),
                chapterId,
                request.blockIndex(),
                request.emoji()));
    }

    /** GET /api/passages/emojis — le jeu d'emojis proposé, pour que l'app suive le back. */
    @GetMapping("/passages/emojis")
    public ResponseEntity<List<String>> emojis() {
        return ResponseEntity.ok(PassageSocialService.ALLOWED_EMOJIS);
    }

    /** DELETE /api/passages/annotations/{id} — retire un de mes messages (204). */
    @DeleteMapping("/passages/annotations/{annotationId}")
    public ResponseEntity<Void> delete(
            @PathVariable Long annotationId,
            Authentication authentication) {
        passageService.delete(authentication.getName(), annotationId);
        return ResponseEntity.noContent().build();
    }
}
