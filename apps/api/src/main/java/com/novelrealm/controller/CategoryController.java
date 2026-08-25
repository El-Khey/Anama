package com.novelrealm.controller;

import java.util.List;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.novelrealm.dto.library.AddNovelToCategoryRequest;
import com.novelrealm.dto.library.CategoryDetailResponse;
import com.novelrealm.dto.library.CategoryNameRequest;
import com.novelrealm.dto.novel.NovelResponse;
import com.novelrealm.model.Category;
import com.novelrealm.model.Novel;
import com.novelrealm.service.library.CategoryService;

/**
 * API des étagères personnelles. Toujours implicitement « l'utilisateur
 * connecté » : l'id user vient du cookie de session, jamais de l'URL.
 */
@RestController
@RequestMapping("/api/categories")
public class CategoryController {

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    /** GET /api/categories — liste des étagères (romans inclus). */
    @GetMapping
    public ResponseEntity<List<CategoryDetailResponse>> list(Authentication authentication) {
        List<CategoryDetailResponse> body = categoryService.listForUser(authentication.getName())
                .stream()
                .map(CategoryController::toDetail)
                .toList();
        return ResponseEntity.ok(body);
    }

    /** POST /api/categories — crée une étagère (201). */
    @PostMapping
    public ResponseEntity<CategoryDetailResponse> create(
            @Valid @RequestBody CategoryNameRequest request,
            Authentication authentication) {
        Category category = categoryService.create(authentication.getName(), request.name());
        return ResponseEntity.status(HttpStatus.CREATED).body(toDetail(category));
    }

    /** GET /api/categories/{id} — détail d'une étagère (romans inclus). */
    @GetMapping("/{id}")
    public ResponseEntity<CategoryDetailResponse> detail(
            @PathVariable Long id,
            Authentication authentication) {
        Category category = categoryService.getDetail(authentication.getName(), id);
        return ResponseEntity.ok(toDetail(category));
    }

    /** PATCH /api/categories/{id} — renomme une étagère. */
    @PatchMapping("/{id}")
    public ResponseEntity<CategoryDetailResponse> rename(
            @PathVariable Long id,
            @Valid @RequestBody CategoryNameRequest request,
            Authentication authentication) {
        Category category = categoryService.rename(authentication.getName(), id, request.name());
        return ResponseEntity.ok(toDetail(category));
    }

    /** DELETE /api/categories/{id} — supprime une étagère (204). */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable Long id,
            Authentication authentication) {
        categoryService.delete(authentication.getName(), id);
        return ResponseEntity.noContent().build();
    }

    /** POST /api/categories/{id}/novels — ajoute un roman à l'étagère. */
    @PostMapping("/{id}/novels")
    public ResponseEntity<CategoryDetailResponse> addNovel(
            @PathVariable Long id,
            @Valid @RequestBody AddNovelToCategoryRequest request,
            Authentication authentication) {
        Category category = categoryService.addNovel(authentication.getName(), id, request.novelId());
        return ResponseEntity.ok(toDetail(category));
    }

    /** DELETE /api/categories/{id}/novels/{novelId} — retire un roman de l'étagère (204). */
    @DeleteMapping("/{id}/novels/{novelId}")
    public ResponseEntity<Void> removeNovel(
            @PathVariable Long id,
            @PathVariable Long novelId,
            Authentication authentication) {
        categoryService.removeNovel(authentication.getName(), id, novelId);
        return ResponseEntity.noContent().build();
    }

    // ── Mapping entité → DTO ────────────────────────────────────────────────

    /** Étagère → DTO : nom + romans complets. */
    private static CategoryDetailResponse toDetail(Category category) {
        List<NovelResponse> novels = category.getNovels().stream()
                .map(CategoryController::toNovelResponse)
                .toList();
        return new CategoryDetailResponse(category.getId(), category.getName(), novels);
    }

    private static NovelResponse toNovelResponse(Novel novel) {
        return new NovelResponse(
                novel.getId(),
                novel.getTitle(),
                novel.getAuthor(),
                novel.getDescription(),
                novel.getCoverUrl(),
                novel.getStatus(),
                novel.getCreatedAt());
    }
}
