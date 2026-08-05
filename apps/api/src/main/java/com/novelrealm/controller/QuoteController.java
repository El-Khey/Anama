package com.novelrealm.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.novelrealm.dto.CreateQuoteRequest;
import com.novelrealm.dto.NovelQuoteCountResponse;
import com.novelrealm.dto.PageResponse;
import com.novelrealm.dto.QuoteAnchorResponse;
import com.novelrealm.dto.QuoteResponse;
import com.novelrealm.service.QuoteService;

import jakarta.validation.Valid;

/**
 * API des citations personnelles (#41, §3).
 *
 * <p>Une citation appartient toujours à l'utilisateur connecté : la collection se lit
 * sous {@code /api/me/quotes} et aucun identifiant d'utilisateur ne circule dans les
 * URL. Personne ne peut donc consulter la collection d'un autre.
 */
@RestController
@RequestMapping("/api")
public class QuoteController {

    private final QuoteService quoteService;

    public QuoteController(QuoteService quoteService) {
        this.quoteService = quoteService;
    }

    /** POST /api/chapters/{chapterId}/quotes — range un passage dans ma collection. */
    @PostMapping("/chapters/{chapterId}/quotes")
    public ResponseEntity<QuoteResponse> create(
            @PathVariable Long chapterId,
            @Valid @RequestBody CreateQuoteRequest request,
            Authentication authentication) {
        QuoteResponse created = quoteService.create(
                authentication.getName(),
                chapterId,
                request.blockIndex(),
                request.startOffset(),
                request.endOffset());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * GET /api/me/quotes?novelId=&q=&sort=&days=&page=&size= — ma collection.
     *
     * <p>Les critères se combinent : {@code novelId} restreint à un roman, {@code q}
     * cherche dans le texte cité, {@code sort} vaut {@code recent} (défaut) ou
     * {@code oldest}, {@code days} ne garde que les N derniers jours ({@code 0} =
     * depuis toujours). Tous ont une valeur par défaut, donc l'appel nu reste valable.
     */
    @GetMapping("/me/quotes")
    public ResponseEntity<PageResponse<QuoteResponse>> list(
            @RequestParam(required = false) Long novelId,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "recent") String sort,
            @RequestParam(defaultValue = "0") int days,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication) {
        return ResponseEntity.ok(PageResponse.from(
                quoteService.list(authentication.getName(), novelId, q, sort, days, page, size)));
    }

    /** GET /api/me/quotes/counts — nombre de mes citations par roman. */
    @GetMapping("/me/quotes/counts")
    public ResponseEntity<List<NovelQuoteCountResponse>> counts(Authentication authentication) {
        return ResponseEntity.ok(quoteService.countsByNovel(authentication.getName()));
    }

    /**
     * GET /api/quotes/{quoteId}/anchor — où retrouver ce passage aujourd'hui.
     *
     * <p>Endpoint séparé de la liste à dessein : c'est le seul appel qui lit le texte
     * intégral d'un chapitre, et il n'a de sens qu'au moment où l'on veut y retourner.
     */
    @GetMapping("/quotes/{quoteId}/anchor")
    public ResponseEntity<QuoteAnchorResponse> anchor(
            @PathVariable Long quoteId,
            Authentication authentication) {
        return ResponseEntity.ok(quoteService.resolveAnchor(authentication.getName(), quoteId));
    }

    /** DELETE /api/quotes/{quoteId} — retire une citation de ma collection (204). */
    @DeleteMapping("/quotes/{quoteId}")
    public ResponseEntity<Void> delete(
            @PathVariable Long quoteId,
            Authentication authentication) {
        quoteService.delete(authentication.getName(), quoteId);
        return ResponseEntity.noContent().build();
    }
}
