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

import com.novelrealm.dto.library.AddLibraryEntryRequest;
import com.novelrealm.dto.library.LibraryEntryResponse;
import com.novelrealm.dto.library.UpdateLibraryStatusRequest;
import com.novelrealm.dto.novel.NovelResponse;
import com.novelrealm.model.LibraryEntry;
import com.novelrealm.model.Novel;
import com.novelrealm.service.library.LibraryEntryService;

/**
 * API de la bibliothèque personnelle. Toujours implicitement « l'utilisateur
 * connecté » : on ne prend jamais l'id user dans l'URL, on le déduit du cookie
 * de session via {@code authentication.getName()} (= l'email).
 */
@RestController
@RequestMapping("/api/library")
public class LibraryEntryController {

    private final LibraryEntryService libraryEntryService;

    public LibraryEntryController(LibraryEntryService libraryEntryService) {
        this.libraryEntryService = libraryEntryService;
    }

    /** GET /api/library — la bibliothèque de l'utilisateur connecté. */
    @GetMapping
    public ResponseEntity<List<LibraryEntryResponse>> list(Authentication authentication) {
        List<LibraryEntryResponse> body = libraryEntryService.listForUser(authentication.getName())
                .stream()
                .map(LibraryEntryController::toResponse)
                .toList();
        return ResponseEntity.ok(body);
    }

    /** POST /api/library — ajoute un roman (201 Created). */
    @PostMapping
    public ResponseEntity<LibraryEntryResponse> add(
            @Valid @RequestBody AddLibraryEntryRequest request,
            Authentication authentication) {
        LibraryEntry entry = libraryEntryService.add(
                authentication.getName(), request.novelId(), request.status());
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(entry));
    }

    /** PATCH /api/library/{novelId} — change le statut de lecture. */
    @PatchMapping("/{novelId}")
    public ResponseEntity<LibraryEntryResponse> updateStatus(
            @PathVariable Long novelId,
            @Valid @RequestBody UpdateLibraryStatusRequest request,
            Authentication authentication) {
        LibraryEntry entry = libraryEntryService.updateStatus(
                authentication.getName(), novelId, request.status());
        return ResponseEntity.ok(toResponse(entry));
    }

    /** DELETE /api/library/{novelId} — retire le roman (204 No Content). */
    @DeleteMapping("/{novelId}")
    public ResponseEntity<Void> remove(
            @PathVariable Long novelId,
            Authentication authentication) {
        libraryEntryService.remove(authentication.getName(), novelId);
        return ResponseEntity.noContent().build();
    }

    /** Entité → DTO : imbrique les infos du roman dans la réponse. */
    private static LibraryEntryResponse toResponse(LibraryEntry entry) {
        Novel novel = entry.getNovel();
        NovelResponse novelBody = new NovelResponse(
                novel.getId(),
                novel.getTitle(),
                novel.getAuthor(),
                novel.getDescription(),
                novel.getCoverUrl(),
                novel.getStatus(),
                novel.getCreatedAt());
        return new LibraryEntryResponse(novelBody, entry.getStatus(), entry.getAddedAt());
    }
}
