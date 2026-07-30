package com.novelrealm.service;

import com.novelrealm.model.Novel;
import com.novelrealm.model.Novel.NovelStatus;
import com.novelrealm.repository.NovelRepository;
import com.novelrealm.exception.NovelNotFoundException;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;


@Service
public class NovelService {

    // Bornes de pagination (garde-fous contre des valeurs aberrantes).
    private static final int MAX_PAGE_SIZE = 100;

    private final NovelRepository novelRepository;

    public NovelService(NovelRepository novelRepository) {
        this.novelRepository = novelRepository;
    }

    public List<Novel> findAll() {
        return this.novelRepository.findAll();
    }

    /**
     * Recherche/filtre/tri paginé du catalogue. Critères optionnels et
     * combinables ({@code q}, {@code status}, {@code genreId}). Tri au choix :
     * {@code recent} (défaut), {@code title} (A→Z), {@code popularity},
     * {@code rating}.
     */
    public Page<Novel> search(String q, NovelStatus status, Long genreId, String sort, int page, int size) {
        // Motif LIKE précalculé (minuscules, encadré de %) ou null si pas de recherche.
        String pattern = (q != null && !q.isBlank()) ? "%" + q.strip().toLowerCase() + "%" : null;
        int safePage = Math.max(0, page);
        int safeSize = Math.clamp(size, 1, MAX_PAGE_SIZE);

        return switch (sort == null ? "recent" : sort.toLowerCase()) {
            case "title" -> novelRepository.search(pattern, status, genreId,
                    PageRequest.of(safePage, safeSize, Sort.by("title").ascending()));
            // Le tri popularité est porté par la requête → pagination seule.
            case "popularity" -> novelRepository.searchByPopularity(pattern, status, genreId,
                    PageRequest.of(safePage, safeSize));
            // Idem pour la note moyenne (les sans-avis sont relégués en fin de liste).
            case "rating" -> novelRepository.searchByRating(pattern, status, genreId,
                    PageRequest.of(safePage, safeSize));
            // "recent" (défaut) : les derniers ajoutés d'abord.
            default -> novelRepository.search(pattern, status, genreId,
                    PageRequest.of(safePage, safeSize, Sort.by("createdAt").descending()));
        };
    }

    public Novel findById(Long id) {
        return novelRepository.findById(id)
                .orElseThrow(() -> new NovelNotFoundException(id));
    }

    /** Détail d'un roman avec ses genres chargés (pour la fiche). */
    @Transactional(readOnly = true)
    public Novel findWithGenresById(Long id) {
        return novelRepository.findWithGenresById(id)
                .orElseThrow(() -> new NovelNotFoundException(id));
    }
}
