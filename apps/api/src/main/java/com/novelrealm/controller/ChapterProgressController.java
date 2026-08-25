package com.novelrealm.controller;

import java.util.List;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.novelrealm.dto.progress.BatchMarkChaptersReadRequest;
import com.novelrealm.dto.progress.ChapterProgressResponse;
import com.novelrealm.dto.progress.MarkChapterReadRequest;
import com.novelrealm.dto.progress.NovelProgressSummary;
import com.novelrealm.dto.progress.SaveChapterPositionRequest;
import com.novelrealm.model.ChapterProgress;
import com.novelrealm.service.progress.ChapterProgressService;

/**
 * API de la progression de lecture. Toujours implicitement « l'utilisateur
 * connecté » (déduit du cookie de session).
 */
@RestController
@RequestMapping("/api/progress")
public class ChapterProgressController {

    private final ChapterProgressService progressService;

    public ChapterProgressController(ChapterProgressService progressService) {
        this.progressService = progressService;
    }

    /** PUT /api/progress/chapters/{chapterId} — marque le chapitre lu / non lu. */
    @PutMapping("/chapters/{chapterId}")
    public ResponseEntity<ChapterProgressResponse> setRead(
            @PathVariable Long chapterId,
            @Valid @RequestBody MarkChapterReadRequest request,
            Authentication authentication) {
        ChapterProgress progress = progressService.setRead(
                authentication.getName(), chapterId, request.read());
        return ResponseEntity.ok(toResponse(progress));
    }

    /** PUT /api/progress/chapters/batch — marque un lot de chapitres lus / non lus. */
    @PutMapping("/chapters/batch")
    public ResponseEntity<List<ChapterProgressResponse>> setReadBatch(
            @Valid @RequestBody BatchMarkChaptersReadRequest request,
            Authentication authentication) {
        List<ChapterProgressResponse> body = progressService
                .setReadBatch(authentication.getName(), request.chapterIds(), request.read())
                .stream()
                .map(ChapterProgressController::toResponse)
                .toList();
        return ResponseEntity.ok(body);
    }

    /** GET /api/progress/novels/{novelId} — progression sur tous les chapitres du roman. */
    @GetMapping("/novels/{novelId}")
    public ResponseEntity<List<ChapterProgressResponse>> novelProgress(
            @PathVariable Long novelId,
            Authentication authentication) {
        List<ChapterProgressResponse> body = progressService
                .getNovelProgress(authentication.getName(), novelId)
                .stream()
                .map(ChapterProgressController::toResponse)
                .toList();
        return ResponseEntity.ok(body);
    }

    /** PUT /api/progress/chapters/{chapterId}/position — sauvegarde la position de reprise (%). */
    @PutMapping("/chapters/{chapterId}/position")
    public ResponseEntity<ChapterProgressResponse> savePosition(
            @PathVariable Long chapterId,
            @Valid @RequestBody SaveChapterPositionRequest request,
            Authentication authentication) {
        ChapterProgress progress = progressService.savePosition(
                authentication.getName(), chapterId, request.percent());
        return ResponseEntity.ok(toResponse(progress));
    }

    /** GET /api/progress/summary — total & lus par roman (chapitres restants côté front). */
    @GetMapping("/summary")
    public ResponseEntity<List<NovelProgressSummary>> summary(Authentication authentication) {
        return ResponseEntity.ok(progressService.getSummary(authentication.getName()));
    }

    /**
     * Entité → DTO. {@code getChapter().getId()} est sûr même en LAZY : lire
     * l'identifiant d'un proxy n'entraîne pas son initialisation.
     */
    private static ChapterProgressResponse toResponse(ChapterProgress progress) {
        return new ChapterProgressResponse(
                progress.getChapter().getId(),
                progress.isRead(),
                progress.getScrollPosition(),
                progress.getReadAt());
    }
}
