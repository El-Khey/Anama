package com.novelrealm.controller;

import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.novelrealm.dto.common.PageResponse;
import com.novelrealm.dto.progress.HistoryEntryResponse;
import com.novelrealm.model.Chapter;
import com.novelrealm.model.ChapterProgress;
import com.novelrealm.model.Novel;
import com.novelrealm.service.progress.HistoryService;

/**
 * API de l'historique de lecture (issue #15). Toujours implicitement
 * « l'utilisateur connecté » (déduit du cookie de session).
 */
@RestController
@RequestMapping("/api/history")
public class HistoryController {

    private final HistoryService historyService;

    public HistoryController(HistoryService historyService) {
        this.historyService = historyService;
    }

    /**
     * GET /api/history?novelId=&sort=&page=&size= — l'historique paginé.
     * Tri {@code date} (défaut) ou {@code novel} ; filtre optionnel par roman.
     */
    @GetMapping
    public ResponseEntity<PageResponse<HistoryEntryResponse>> list(
            @RequestParam(required = false) Long novelId,
            @RequestParam(defaultValue = "date") String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication) {
        Page<HistoryEntryResponse> result = historyService
                .getHistory(authentication.getName(), novelId, sort, page, size)
                .map(HistoryController::toResponse);
        return ResponseEntity.ok(PageResponse.from(result));
    }

    /** DELETE /api/history — vide tout l'historique (204 No Content). */
    @DeleteMapping
    public ResponseEntity<Void> clearAll(Authentication authentication) {
        historyService.clearAll(authentication.getName());
        return ResponseEntity.noContent().build();
    }

    /** DELETE /api/history/novels/{novelId} — vide l'historique d'un roman (204 No Content). */
    @DeleteMapping("/novels/{novelId}")
    public ResponseEntity<Void> clearNovel(
            @PathVariable Long novelId,
            Authentication authentication) {
        historyService.clearNovel(authentication.getName(), novelId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Entité → DTO. Le chapitre et son roman sont déjà chargés (@EntityGraph du
     * repository), donc les traverser ici — hors transaction — est sûr.
     */
    private static HistoryEntryResponse toResponse(ChapterProgress progress) {
        Chapter chapter = progress.getChapter();
        Novel novel = chapter.getNovel();
        return new HistoryEntryResponse(
                chapter.getId(),
                chapter.getChapterNumber(),
                chapter.getTitle(),
                novel.getId(),
                novel.getTitle(),
                novel.getCoverUrl(),
                progress.isRead(),
                progress.getScrollPosition(),
                progress.getReadAt());
    }
}
