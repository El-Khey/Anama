package com.novelrealm.service.progress;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.novelrealm.dto.chapter.NovelChapterCount;
import com.novelrealm.dto.progress.NovelProgressSummary;
import com.novelrealm.model.Chapter;
import com.novelrealm.model.ChapterProgress;
import com.novelrealm.model.User;
import com.novelrealm.repository.ChapterProgressRepository;
import com.novelrealm.service.chapter.ChapterService;
import com.novelrealm.service.user.UserService;

/**
 * Logique métier de la progression de lecture.
 *
 * <p>Utilisateur identifié par son email (principal de session). S'appuie sur
 * {@link ChapterService} pour valider l'existence du chapitre (404 sinon).
 */
@Service
public class ChapterProgressService {

    private final ChapterProgressRepository progressRepository;
    private final UserService userService;
    private final ChapterService chapterService;

    public ChapterProgressService(
            ChapterProgressRepository progressRepository,
            UserService userService,
            ChapterService chapterService) {
        this.progressRepository = progressRepository;
        this.userService = userService;
        this.chapterService = chapterService;
    }

    /**
     * Marque un chapitre comme lu / non lu (crée la progression si besoin —
     * « upsert »).
     */
    @Transactional
    public ChapterProgress setRead(String email, Long chapterId, boolean read) {
        User user = userService.findByEmail(email);
        ChapterProgress progress = progressRepository
                .findByUser_IdAndChapter_Id(user.getId(), chapterId)
                .orElseGet(() -> {
                    Chapter chapter = chapterService.findById(chapterId); // 404 si absent
                    return new ChapterProgress(user, chapter);
                });
        progress.setRead(read);
        return progressRepository.save(progress);
    }

    /**
     * Sauvegarde la position de reprise d'un chapitre (en %). Atteindre la fin
     * (100 %) marque automatiquement le chapitre comme lu.
     */
    @Transactional
    public ChapterProgress savePosition(String email, Long chapterId, int percent) {
        User user = userService.findByEmail(email);
        ChapterProgress progress = progressRepository
                .findByUser_IdAndChapter_Id(user.getId(), chapterId)
                .orElseGet(() -> {
                    Chapter chapter = chapterService.findById(chapterId); // 404 si absent
                    return new ChapterProgress(user, chapter);
                });
        progress.setScrollPosition(percent);
        if (percent >= 100) {
            progress.setRead(true);
        }
        return progressRepository.save(progress);
    }

    /**
     * Marque un LOT de chapitres comme lus / non lus en une transaction
     * (sélection multiple). Les ids inconnus sont ignorés. « Upsert » par
     * chapitre : on met à jour la progression existante, on crée les manquantes.
     */
    @Transactional
    public List<ChapterProgress> setReadBatch(String email, List<Long> chapterIds, boolean read) {
        User user = userService.findByEmail(email);

        Map<Long, ChapterProgress> existing = progressRepository
                .findByUser_IdAndChapter_IdIn(user.getId(), chapterIds).stream()
                .collect(Collectors.toMap(p -> p.getChapter().getId(), p -> p));

        List<ChapterProgress> toSave = new ArrayList<>();
        for (Chapter chapter : chapterService.findAllByIds(chapterIds)) {
            ChapterProgress progress = existing.getOrDefault(
                    chapter.getId(), new ChapterProgress(user, chapter));
            progress.setRead(read);
            toSave.add(progress);
        }
        return progressRepository.saveAll(toSave);
    }

    /** Progression de l'utilisateur sur tous les chapitres d'un roman. */
    @Transactional(readOnly = true)
    public List<ChapterProgress> getNovelProgress(String email, Long novelId) {
        User user = userService.findByEmail(email);
        return progressRepository.findByUser_IdAndChapter_Novel_Id(user.getId(), novelId);
    }

    /**
     * Résumé de progression par roman : total de chapitres + nombre lus. On
     * part du total (tous les romans) et on y greffe le nombre de chapitres lus
     * par l'utilisateur (0 s'il n'a rien lu).
     */
    @Transactional(readOnly = true)
    public List<NovelProgressSummary> getSummary(String email) {
        User user = userService.findByEmail(email);

        Map<Long, Long> readByNovel = progressRepository.countReadPerNovel(user.getId()).stream()
                .collect(Collectors.toMap(NovelChapterCount::novelId, NovelChapterCount::count));

        return chapterService.countChaptersPerNovel().stream()
                .map(total -> new NovelProgressSummary(
                        total.novelId(),
                        total.count(),
                        readByNovel.getOrDefault(total.novelId(), 0L)))
                .toList();
    }
}
