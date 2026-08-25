package com.novelrealm.service.user;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.novelrealm.dto.chapter.NovelChapterCount;
import com.novelrealm.dto.user.UserStatsResponse;
import com.novelrealm.model.User;
import com.novelrealm.repository.ChapterFavoriteRepository;
import com.novelrealm.repository.ChapterProgressRepository;
import com.novelrealm.repository.LibraryEntryRepository;
import com.novelrealm.service.chapter.ChapterService;

/**
 * Statistiques de lecture résumées du profil (issue #17). Les stats détaillées
 * (graphiques, temps de lecture…) viendront avec l'issue #23.
 */
@Service
public class UserStatsService {

    private final UserService userService;
    private final ChapterService chapterService;
    private final ChapterProgressRepository progressRepository;
    private final LibraryEntryRepository libraryEntryRepository;
    private final ChapterFavoriteRepository favoriteRepository;

    public UserStatsService(
            UserService userService,
            ChapterService chapterService,
            ChapterProgressRepository progressRepository,
            LibraryEntryRepository libraryEntryRepository,
            ChapterFavoriteRepository favoriteRepository) {
        this.userService = userService;
        this.chapterService = chapterService;
        this.progressRepository = progressRepository;
        this.libraryEntryRepository = libraryEntryRepository;
        this.favoriteRepository = favoriteRepository;
    }

    @Transactional(readOnly = true)
    public UserStatsResponse getStats(String email) {
        User user = userService.findByEmail(email);
        Long userId = user.getId();

        long chaptersRead = progressRepository.countByUser_IdAndReadTrue(userId);
        long novelsFollowed = libraryEntryRepository.countByUserId(userId);
        long chaptersFavorited = favoriteRepository.countByUser_Id(userId);
        long novelsCompleted = countCompletedNovels(userId);

        List<LocalDate> days = progressRepository.findActivityDates(userId).stream()
                .sorted()
                .toList();

        return new UserStatsResponse(
                chaptersRead,
                novelsFollowed,
                novelsCompleted,
                chaptersFavorited,
                days.size(),
                currentStreak(days),
                longestStreak(days));
    }

    /** Romans dont TOUS les chapitres sont lus (total > 0). */
    private long countCompletedNovels(Long userId) {
        Map<Long, Long> readByNovel = progressRepository.countReadPerNovel(userId).stream()
                .collect(Collectors.toMap(NovelChapterCount::novelId, NovelChapterCount::count));

        return chapterService.countChaptersPerNovel().stream()
                .filter(total -> total.count() > 0
                        && readByNovel.getOrDefault(total.novelId(), 0L) >= total.count())
                .count();
    }

    /**
     * Série en cours : jours consécutifs d'activité en remontant depuis
     * aujourd'hui (ou hier — la série d'hier n'est pas « cassée » tant que la
     * journée n'est pas finie).
     */
    private static long currentStreak(List<LocalDate> days) {
        Set<LocalDate> lookup = new HashSet<>(days);
        LocalDate today = LocalDate.now();
        LocalDate cursor = lookup.contains(today) ? today
                : lookup.contains(today.minusDays(1)) ? today.minusDays(1)
                : null;

        long streak = 0;
        while (cursor != null && lookup.contains(cursor)) {
            streak++;
            cursor = cursor.minusDays(1);
        }
        return streak;
    }

    /** Plus longue série de jours consécutifs (liste triée croissante). */
    private static long longestStreak(List<LocalDate> days) {
        long best = 0;
        long run = 0;
        LocalDate previous = null;
        for (LocalDate day : days) {
            run = (previous != null && previous.plusDays(1).equals(day)) ? run + 1 : 1;
            best = Math.max(best, run);
            previous = day;
        }
        return best;
    }
}
