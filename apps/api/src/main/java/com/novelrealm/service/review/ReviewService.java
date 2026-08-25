package com.novelrealm.service.review;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

import com.novelrealm.dto.review.ReviewSummaryResponse;
import com.novelrealm.exception.review.ReviewNotFoundException;
import com.novelrealm.model.Novel;
import com.novelrealm.model.Review;
import com.novelrealm.model.User;
import com.novelrealm.repository.ReviewRepository;
import com.novelrealm.service.novel.NovelService;
import com.novelrealm.service.user.UserService;

/**
 * Avis des utilisateurs sur les romans. Un utilisateur n'a qu'UN avis par
 * roman : {@link #upsert} le crée ou le met à jour, sans jamais en empiler.
 */
@Service
public class ReviewService {

    /** Garde-fou de pagination : au-delà, on plafonne. */
    private static final int MAX_PAGE_SIZE = 50;

    private final ReviewRepository reviewRepository;
    private final UserService userService;
    private final NovelService novelService;

    public ReviewService(
            ReviewRepository reviewRepository,
            UserService userService,
            NovelService novelService) {
        this.reviewRepository = reviewRepository;
        this.userService = userService;
        this.novelService = novelService;
    }

    /** Les avis d'un roman, les plus récents d'abord. */
    @Transactional(readOnly = true)
    public Page<Review> listForNovel(Long novelId, int page, int size) {
        novelService.findById(novelId); // 404 si le roman n'existe pas
        int safeSize = Math.clamp(size, 1, MAX_PAGE_SIZE);
        return reviewRepository.findByNovel_Id(
                novelId,
                PageRequest.of(Math.max(page, 0), safeSize, Sort.by(Sort.Direction.DESC, "createdAt")));
    }

    /** L'avis de l'utilisateur connecté sur ce roman, ou 404 s'il n'en a pas. */
    @Transactional(readOnly = true)
    public Review getOwn(String email, Long novelId) {
        User user = userService.findByEmail(email);
        return reviewRepository.findByUser_IdAndNovel_Id(user.getId(), novelId)
                .orElseThrow(() -> new ReviewNotFoundException(novelId));
    }

    /**
     * Dépose SON avis, ou met à jour celui qui existe déjà. Le commentaire vide
     * est ramené à {@code null} : « pas de commentaire » et « commentaire vide »
     * sont la même chose.
     */
    @Transactional
    public Review upsert(String email, Long novelId, int rating, String body) {
        User user = userService.findByEmail(email);
        Novel novel = novelService.findById(novelId);
        String cleaned = normalizeBody(body);

        return reviewRepository.findByUser_IdAndNovel_Id(user.getId(), novelId)
                .map(existing -> {
                    existing.setRating((short) rating);
                    existing.setBody(cleaned);
                    return existing; // @Transactional → flush automatique
                })
                .orElseGet(() -> reviewRepository.save(
                        new Review(user, novel, (short) rating, cleaned)));
    }

    /** Retire SON avis. 404 s'il n'y en avait pas. */
    @Transactional
    public void deleteOwn(String email, Long novelId) {
        reviewRepository.delete(getOwn(email, novelId));
    }

    /** Moyenne et nombre d'avis d'un roman (calculés en base). */
    @Transactional(readOnly = true)
    public ReviewRepository.RatingSummary summarize(Long novelId) {
        return reviewRepository.summarizeByNovelId(novelId);
    }

    /**
     * Synthèse complète pour l'histogramme : moyenne, total et répartition par
     * note. Les cinq notes sont toujours présentes (à zéro si personne ne l'a
     * donnée) pour que le client n'ait pas à compléter les trous.
     */
    @Transactional(readOnly = true)
    public ReviewSummaryResponse summarizeWithDistribution(Long novelId) {
        novelService.findById(novelId); // 404 si le roman n'existe pas

        Map<Integer, Long> counts = reviewRepository.countByRating(novelId).stream()
                .collect(Collectors.toMap(
                        ReviewRepository.RatingBucket::getRating,
                        ReviewRepository.RatingBucket::getCount));

        Map<Integer, Long> distribution = new LinkedHashMap<>();
        for (int star = 5; star >= 1; star--) {
            distribution.put(star, counts.getOrDefault(star, 0L));
        }

        ReviewRepository.RatingSummary summary = reviewRepository.summarizeByNovelId(novelId);
        return new ReviewSummaryResponse(summary.getAverage(), summary.getCount(), distribution);
    }

    private static String normalizeBody(String body) {
        if (body == null) {
            return null;
        }
        String cleaned = body.strip();
        return cleaned.isEmpty() ? null : cleaned;
    }
}
