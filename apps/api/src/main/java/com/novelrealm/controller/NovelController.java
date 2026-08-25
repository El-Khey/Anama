package com.novelrealm.controller;

import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.util.Comparator;
import java.util.List;

import com.novelrealm.dto.common.PageResponse;
import com.novelrealm.dto.novel.GenreResponse;
import com.novelrealm.dto.novel.NovelDetailResponse;
import com.novelrealm.dto.novel.NovelResponse;
import com.novelrealm.model.Novel;
import com.novelrealm.model.Novel.NovelStatus;
import com.novelrealm.repository.ReviewRepository.RatingSummary;
import com.novelrealm.service.novel.NovelService;
import com.novelrealm.service.review.ReviewService;

@RestController
@RequestMapping("/api/novels")
public class NovelController {
    private final NovelService novelService;
    private final ReviewService reviewService;

    public NovelController(NovelService novelService, ReviewService reviewService) {
        this.novelService = novelService;
        this.reviewService = reviewService;
    }

    /**
     * Catalogue : recherche / filtre / tri, paginé. Tous les paramètres sont
     * optionnels et combinables.
     *
     * @param q       recherche titre ou auteur (insensible à la casse)
     * @param genreId filtre par genre
     * @param status  filtre par statut ({@code ONGOING} / {@code COMPLETED})
     * @param sort    {@code recent} (défaut) · {@code title} · {@code popularity} ·
     *                {@code rating}
     * @param page    numéro de page (défaut 0)
     * @param size    taille de page (défaut 20, max 100)
     */
    @GetMapping
    public ResponseEntity<PageResponse<NovelResponse>> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Long genreId,
            @RequestParam(required = false) NovelStatus status,
            @RequestParam(defaultValue = "recent") String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<NovelResponse> result = novelService.search(q, status, genreId, sort, page, size)
                .map(NovelController::toResponse);
        return ResponseEntity.ok(PageResponse.from(result));
    }

    /** Fiche détail d'un roman (genres + note moyenne). */
    @GetMapping("/{id}")
    public ResponseEntity<NovelDetailResponse> findById(@PathVariable Long id) {
        Novel novel = novelService.findWithGenresById(id);
        return ResponseEntity.ok(toDetailResponse(novel, reviewService.summarize(id)));
    }

    /** Entité → DTO (résumé de roman). */
    private static NovelResponse toResponse(Novel novel) {
        return new NovelResponse(
                novel.getId(),
                novel.getTitle(),
                novel.getAuthor(),
                novel.getDescription(),
                novel.getCoverUrl(),
                novel.getStatus(),
                novel.getCreatedAt());
    }

    /** Entité → DTO détail (résumé + genres triés par nom + note moyenne). */
    private static NovelDetailResponse toDetailResponse(Novel novel, RatingSummary rating) {
        List<GenreResponse> genres = novel.getGenres().stream()
                .map(g -> new GenreResponse(g.getId(), g.getName()))
                .sorted(Comparator.comparing(GenreResponse::name))
                .toList();
        return new NovelDetailResponse(
                novel.getId(),
                novel.getTitle(),
                novel.getAuthor(),
                novel.getDescription(),
                novel.getCoverUrl(),
                novel.getStatus(),
                novel.getCreatedAt(),
                genres,
                rating.getAverage(),
                rating.getCount());
    }
}
