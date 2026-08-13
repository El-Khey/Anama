package com.novelrealm.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.novelrealm.dto.GifAvailabilityResponse;
import com.novelrealm.dto.GifPageResponse;
import com.novelrealm.service.GifService;

/**
 * Recherche de GIF (issue #45, §5) — proxy Tenor, la clé d'API ne quitte jamais
 * le serveur. Routes authentifiées comme tout le reste : le quota Tenor est
 * ainsi réservé à nos utilisateurs, pas à quiconque scanne des ports.
 */
@RestController
@RequestMapping("/api/gifs")
public class GifController {

    private final GifService gifService;

    public GifController(GifService gifService) {
        this.gifService = gifService;
    }

    /**
     * GET /api/gifs/availability — la fonctionnalité est-elle configurée ici ?
     * L'app le demande une fois et masque le bouton GIF si non.
     */
    @GetMapping("/availability")
    public ResponseEntity<GifAvailabilityResponse> availability() {
        return ResponseEntity.ok(new GifAvailabilityResponse(gifService.isAvailable()));
    }

    /** GET /api/gifs/search?q=&limit=&pos= — recherche plein texte, paginée par curseur. */
    @GetMapping("/search")
    public ResponseEntity<GifPageResponse> search(
            @RequestParam("q") String query,
            @RequestParam(defaultValue = "24") int limit,
            @RequestParam(required = false) String pos) {
        return ResponseEntity.ok(gifService.search(query, limit, pos));
    }

    /** GET /api/gifs/featured?limit=&pos= — les GIF du moment (sélecteur avant saisie). */
    @GetMapping("/featured")
    public ResponseEntity<GifPageResponse> featured(
            @RequestParam(defaultValue = "24") int limit,
            @RequestParam(required = false) String pos) {
        return ResponseEntity.ok(gifService.featured(limit, pos));
    }
}
