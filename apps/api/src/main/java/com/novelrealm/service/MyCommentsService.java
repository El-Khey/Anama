package com.novelrealm.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.novelrealm.dto.MyCommentResponse;
import com.novelrealm.dto.PageResponse;
import com.novelrealm.model.PassageAnnotation;
import com.novelrealm.model.User;
import com.novelrealm.repository.ChapterCommentRepository;
import com.novelrealm.repository.ChapterCommentRepository.MyChapterCommentView;
import com.novelrealm.repository.ChapterRepository;
import com.novelrealm.repository.PassageAnnotationRepository;
import com.novelrealm.repository.PassageAnnotationRepository.MyPassageCommentView;

/**
 * « Mes commentaires » (issue #45, §4) : TOUT ce que l'utilisateur a écrit, en un
 * seul flux trié par date — alors que les messages vivent dans deux tables
 * (fin de chapitre, passage).
 *
 * <p><b>Pagination d'un flux fusionné.</b> Servir la page N du mélange demande
 * les (N+1)×taille premières lignes de CHAQUE source, un tri-fusion, puis la
 * découpe. C'est plus de lignes que la page seule, mais des lignes de
 * projection minuscules, sur les commentaires d'UN utilisateur — jamais des
 * milliers. L'alternative (une vue UNION en SQL natif) coûterait sa maintenance
 * à chaque évolution de schéma pour un gain nul à cette échelle.
 *
 * <p><b>Les extraits de passage sont résolus par chapitre distinct.</b> Un
 * commentaire de passage sans son paragraphe est illisible ; le paragraphe vit
 * dans {@code chapters.content}, qu'on ne charge que pour les chapitres de LA
 * page servie (rarement plus d'une poignée), et qu'on résout par ancre — comme
 * le lecteur — pour survivre aux ré-ingestions.
 */
@Service
public class MyCommentsService {

    private static final int MAX_PAGE_SIZE = 50;

    /** Longueur des extraits de passage — reconnaître le paragraphe, pas le relire. */
    private static final int EXCERPT_LENGTH = 160;

    private final ChapterCommentRepository chapterCommentRepository;
    private final PassageAnnotationRepository annotationRepository;
    private final ChapterRepository chapterRepository;
    private final UserService userService;

    public MyCommentsService(
            ChapterCommentRepository chapterCommentRepository,
            PassageAnnotationRepository annotationRepository,
            ChapterRepository chapterRepository,
            UserService userService) {
        this.chapterCommentRepository = chapterCommentRepository;
        this.annotationRepository = annotationRepository;
        this.chapterRepository = chapterRepository;
        this.userService = userService;
    }

    /** Une page du flux fusionné, la plus récente en tête. */
    @Transactional(readOnly = true)
    public PageResponse<MyCommentResponse> list(String email, int page, int size) {
        User user = userService.findByEmail(email);
        int safePage = Math.max(page, 0);
        int safeSize = Math.clamp(size, 1, MAX_PAGE_SIZE);

        // Les (page+1)×taille premières lignes de chaque source suffisent à
        // composer la page demandée, quel que soit l'entrelacement des dates.
        Pageable top = PageRequest.of(0, (safePage + 1) * safeSize);
        List<MyChapterCommentView> chapterRows = chapterCommentRepository.findMine(user.getId(), top);
        List<MyPassageCommentView> passageRows = annotationRepository.findMyComments(user.getId(), top);

        long total = chapterCommentRepository.countByUser_IdAndDeletedAtIsNull(user.getId())
                + annotationRepository.countByUser_IdAndKind(user.getId(), PassageAnnotation.Kind.COMMENT);

        // Tri-fusion, puis découpe de la page demandée.
        List<Object> merged = new ArrayList<>(chapterRows.size() + passageRows.size());
        merged.addAll(chapterRows);
        merged.addAll(passageRows);
        merged.sort(Comparator.comparing(MyCommentsService::createdAtOf).reversed());

        int from = safePage * safeSize;
        int to = Math.min(from + safeSize, merged.size());
        List<Object> slice = from >= merged.size() ? List.of() : merged.subList(from, to);

        // Extraits de passage : le texte des seuls chapitres présents dans la page.
        Map<Long, List<String>> blocksByChapter = loadBlocks(slice);

        List<MyCommentResponse> content = slice.stream()
                .map(row -> row instanceof MyChapterCommentView chapterRow
                        ? toResponse(chapterRow)
                        : toResponse((MyPassageCommentView) row, blocksByChapter))
                .toList();

        int totalPages = (int) Math.ceil((double) total / safeSize);
        return new PageResponse<>(content, safePage, safeSize, total, totalPages);
    }

    // ── Interne ───────────────────────────────────────────────────────────────

    private static java.time.Instant createdAtOf(Object row) {
        return row instanceof MyChapterCommentView chapterRow
                ? chapterRow.getCreatedAt()
                : ((MyPassageCommentView) row).getCreatedAt();
    }

    /** Le texte découpé des chapitres portant un commentaire de passage de la page. */
    private Map<Long, List<String>> loadBlocks(List<Object> slice) {
        List<Long> chapterIds = slice.stream()
                .filter(row -> row instanceof MyPassageCommentView)
                .map(row -> ((MyPassageCommentView) row).getChapterId())
                .distinct()
                .toList();
        if (chapterIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, List<String>> blocks = new HashMap<>();
        for (var view : chapterRepository.findContentByIdIn(chapterIds)) {
            blocks.put(view.getId(), ChapterBlocks.split(view.getContent()));
        }
        return blocks;
    }

    private static MyCommentResponse toResponse(MyChapterCommentView row) {
        return new MyCommentResponse(
                MyCommentResponse.Kind.CHAPTER,
                row.getId(),
                row.getBody(),
                row.getGifUrl(),
                row.getGifPreviewUrl(),
                false,
                row.getParentId() != null,
                row.getNovelId(),
                row.getNovelTitle(),
                row.getNovelCoverUrl(),
                row.getChapterId(),
                row.getChapterNumber(),
                row.getChapterTitle(),
                null,
                null,
                row.getCreatedAt());
    }

    private static MyCommentResponse toResponse(
            MyPassageCommentView row, Map<Long, List<String>> blocksByChapter) {
        // L'ancre est résolue sur la version ACTUELLE du chapitre : l'index
        // renvoyé est donc directement utilisable comme lien profond. Passage
        // disparu → index null, extrait null : le message reste listé, seul son
        // point d'ancrage s'est perdu.
        Integer resolvedIndex = null;
        String excerpt = null;
        List<String> blocks = blocksByChapter.get(row.getChapterId());
        if (blocks != null) {
            int index = ChapterBlocks
                    .resolve(blocks, row.getBlockIndex(), row.getTextHash())
                    .blockIndex();
            if (index >= 0) {
                resolvedIndex = index;
                excerpt = excerptOf(blocks.get(index));
            }
        }
        return new MyCommentResponse(
                MyCommentResponse.Kind.PASSAGE,
                row.getId(),
                row.getBody(),
                row.getGifUrl(),
                row.getGifPreviewUrl(),
                row.getSpoiler(),
                row.getParentId() != null,
                row.getNovelId(),
                row.getNovelTitle(),
                row.getNovelCoverUrl(),
                row.getChapterId(),
                row.getChapterNumber(),
                row.getChapterTitle(),
                resolvedIndex,
                excerpt,
                row.getCreatedAt());
    }

    private static String excerptOf(String block) {
        String cleaned = Objects.requireNonNullElse(block, "").strip();
        if (cleaned.length() <= EXCERPT_LENGTH) {
            return cleaned;
        }
        return cleaned.substring(0, EXCERPT_LENGTH - 1) + "…";
    }
}
