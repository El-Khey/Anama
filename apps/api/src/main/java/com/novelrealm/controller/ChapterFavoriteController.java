package com.novelrealm.controller;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.novelrealm.dto.progress.ChapterFavoriteResponse;
import com.novelrealm.model.Chapter;
import com.novelrealm.model.ChapterFavorite;
import com.novelrealm.service.progress.ChapterFavoriteService;

/**
 * API des favoris de chapitre (marque-pages). Toujours implicitement
 * « l'utilisateur connecté » : on ne prend jamais l'id user dans l'URL, on le
 * déduit du cookie de session via {@code authentication.getName()} (= l'email).
 */
@RestController
@RequestMapping("/api/favorites")
public class ChapterFavoriteController {

    private final ChapterFavoriteService favoriteService;

    public ChapterFavoriteController(ChapterFavoriteService favoriteService) {
        this.favoriteService = favoriteService;
    }

    /** GET /api/favorites/chapters — tous les chapitres favoris de l'utilisateur. */
    @GetMapping("/chapters")
    public ResponseEntity<List<ChapterFavoriteResponse>> list(Authentication authentication) {
        List<ChapterFavoriteResponse> body = favoriteService.listForUser(authentication.getName())
                .stream()
                .map(ChapterFavoriteController::toResponse)
                .toList();
        return ResponseEntity.ok(body);
    }

    /** GET /api/favorites/novels/{novelId} — favoris de l'utilisateur dans ce roman. */
    @GetMapping("/novels/{novelId}")
    public ResponseEntity<List<ChapterFavoriteResponse>> listForNovel(
            @PathVariable Long novelId,
            Authentication authentication) {
        List<ChapterFavoriteResponse> body = favoriteService
                .listForNovel(authentication.getName(), novelId)
                .stream()
                .map(ChapterFavoriteController::toResponse)
                .toList();
        return ResponseEntity.ok(body);
    }

    /** POST /api/favorites/chapters/{chapterId} — met le chapitre en favori (201 Created). */
    @PostMapping("/chapters/{chapterId}")
    public ResponseEntity<ChapterFavoriteResponse> add(
            @PathVariable Long chapterId,
            Authentication authentication) {
        ChapterFavorite favorite = favoriteService.add(authentication.getName(), chapterId);
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(favorite));
    }

    /** DELETE /api/favorites/chapters/{chapterId} — retire le favori (204 No Content). */
    @DeleteMapping("/chapters/{chapterId}")
    public ResponseEntity<Void> remove(
            @PathVariable Long chapterId,
            Authentication authentication) {
        favoriteService.remove(authentication.getName(), chapterId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Entité → DTO. Lire l'id d'un proxy LAZY ({@code getNovel().getId()}) est
     * sûr ; le chapitre et son roman sont de toute façon chargés en amont via
     * {@code @EntityGraph}.
     */
    private static ChapterFavoriteResponse toResponse(ChapterFavorite favorite) {
        Chapter chapter = favorite.getChapter();
        return new ChapterFavoriteResponse(
                chapter.getId(),
                chapter.getNovel().getId(),
                chapter.getChapterNumber(),
                chapter.getTitle(),
                favorite.getFavoritedAt());
    }
}
