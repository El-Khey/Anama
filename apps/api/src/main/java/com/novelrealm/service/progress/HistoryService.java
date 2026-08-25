package com.novelrealm.service.progress;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.novelrealm.model.ChapterProgress;
import com.novelrealm.model.User;
import com.novelrealm.repository.ChapterProgressRepository;
import com.novelrealm.service.user.UserService;

/**
 * Historique de lecture (issue #15).
 *
 * <p>Un « historique » = les chapitres qu'un utilisateur a ouverts, c.-à-d.
 * toute ligne de {@code chapter_progress} (lu OU simplement entamé). On s'appuie
 * donc directement sur {@link ChapterProgressRepository} : pas de nouvelle table,
 * on ne fait que lire / purger la progression existante.
 *
 * <p>Utilisateur identifié par son email (principal de session) ; toutes les
 * requêtes sont bornées à cet utilisateur.
 */
@Service
public class HistoryService {

    // Garde-fou de pagination (même borne que le catalogue).
    private static final int MAX_PAGE_SIZE = 100;

    private final ChapterProgressRepository progressRepository;
    private final UserService userService;

    public HistoryService(ChapterProgressRepository progressRepository, UserService userService) {
        this.progressRepository = progressRepository;
        this.userService = userService;
    }

    /**
     * Page d'historique de l'utilisateur, éventuellement restreinte à un roman.
     *
     * @param novelId filtre optionnel (null = tous les romans)
     * @param sort    {@code date} (défaut, plus récent d'abord) ou {@code novel}
     *                (par titre de roman puis n° de chapitre)
     */
    @Transactional(readOnly = true)
    public Page<ChapterProgress> getHistory(String email, Long novelId, String sort, int page, int size) {
        User user = userService.findByEmail(email);
        int safePage = Math.max(0, page);
        int safeSize = Math.clamp(size, 1, MAX_PAGE_SIZE);
        PageRequest pageable = PageRequest.of(safePage, safeSize, sortOf(sort));

        return (novelId != null)
                ? progressRepository.findByUser_IdAndChapter_Novel_Id(user.getId(), novelId, pageable)
                : progressRepository.findByUser_Id(user.getId(), pageable);
    }

    /** Vide tout l'historique de l'utilisateur. */
    @Transactional
    public void clearAll(String email) {
        User user = userService.findByEmail(email);
        progressRepository.deleteByUser_Id(user.getId());
    }

    /** Vide l'historique de l'utilisateur pour un roman donné (idempotent). */
    @Transactional
    public void clearNovel(String email, Long novelId) {
        User user = userService.findByEmail(email);
        progressRepository.deleteByUser_IdAndNovel(user.getId(), novelId);
    }

    /** Traduit le paramètre de tri en {@link Sort} (avec départage déterministe). */
    private static Sort sortOf(String sort) {
        return switch (sort == null ? "date" : sort.toLowerCase()) {
            // Un roman lu dans l'ordre : titre puis n° de chapitre.
            case "novel" -> Sort.by(Sort.Order.asc("chapter.novel.title"),
                    Sort.Order.asc("chapter.chapterNumber"));
            // "date" (défaut) : dernières lectures en tête, id en départage stable.
            default -> Sort.by(Sort.Order.desc("readAt"), Sort.Order.desc("chapter.id"));
        };
    }
}
