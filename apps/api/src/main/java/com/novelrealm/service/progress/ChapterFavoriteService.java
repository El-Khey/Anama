package com.novelrealm.service.progress;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.novelrealm.exception.progress.ChapterAlreadyFavoritedException;
import com.novelrealm.exception.progress.ChapterFavoriteNotFoundException;
import com.novelrealm.model.Chapter;
import com.novelrealm.model.ChapterFavorite;
import com.novelrealm.model.User;
import com.novelrealm.repository.ChapterFavoriteRepository;
import com.novelrealm.service.chapter.ChapterService;
import com.novelrealm.service.library.LibraryEntryService;
import com.novelrealm.service.user.UserService;

/**
 * Logique métier des favoris de chapitre (marque-pages).
 *
 * <p>Utilisateur identifié par son email (principal de session). S'appuie sur
 * {@link ChapterService} pour valider l'existence du chapitre (404 sinon), sur
 * le modèle de {@link LibraryEntryService}.
 */
@Service
public class ChapterFavoriteService {

    private final ChapterFavoriteRepository favoriteRepository;
    private final UserService userService;
    private final ChapterService chapterService;

    public ChapterFavoriteService(
            ChapterFavoriteRepository favoriteRepository,
            UserService userService,
            ChapterService chapterService) {
        this.favoriteRepository = favoriteRepository;
        this.userService = userService;
        this.chapterService = chapterService;
    }

    /**
     * Met un chapitre en favori pour l'utilisateur.
     *
     * @throws ChapterAlreadyFavoritedException si le chapitre y est déjà.
     */
    @Transactional
    public ChapterFavorite add(String email, Long chapterId) {
        User user = userService.findByEmail(email);
        if (favoriteRepository.existsByUser_IdAndChapter_Id(user.getId(), chapterId)) {
            throw new ChapterAlreadyFavoritedException(chapterId);
        }
        Chapter chapter = chapterService.findById(chapterId); // 404 si absent
        return favoriteRepository.save(new ChapterFavorite(user, chapter));
    }

    /**
     * Retire un chapitre des favoris de l'utilisateur.
     *
     * @throws ChapterFavoriteNotFoundException si le chapitre n'y est pas.
     */
    @Transactional
    public void remove(String email, Long chapterId) {
        User user = userService.findByEmail(email);
        ChapterFavorite favorite = favoriteRepository
                .findByUser_IdAndChapter_Id(user.getId(), chapterId)
                .orElseThrow(() -> new ChapterFavoriteNotFoundException(chapterId));
        favoriteRepository.delete(favorite);
    }

    /** Favoris de l'utilisateur sur les chapitres d'un roman. */
    @Transactional(readOnly = true)
    public List<ChapterFavorite> listForNovel(String email, Long novelId) {
        User user = userService.findByEmail(email);
        return favoriteRepository.findByUser_IdAndChapter_Novel_IdOrderByFavoritedAtDesc(
                user.getId(), novelId);
    }

    /** Tous les favoris de l'utilisateur (tous romans confondus). */
    @Transactional(readOnly = true)
    public List<ChapterFavorite> listForUser(String email) {
        User user = userService.findByEmail(email);
        return favoriteRepository.findByUser_IdOrderByFavoritedAtDesc(user.getId());
    }
}
