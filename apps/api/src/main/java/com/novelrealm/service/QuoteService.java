package com.novelrealm.service;

import java.util.List;
import java.util.Locale;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.novelrealm.dto.NovelQuoteCountResponse;
import com.novelrealm.dto.QuoteAnchorResponse;
import com.novelrealm.dto.QuoteResponse;
import com.novelrealm.exception.InvalidQuoteException;
import com.novelrealm.exception.QuoteNotFoundException;
import com.novelrealm.model.Chapter;
import com.novelrealm.model.PassageAnnotation;
import com.novelrealm.model.User;
import com.novelrealm.repository.PassageAnnotationRepository;

/**
 * Citations personnelles (#41, §3) : extraire un passage d'un chapitre et le ranger
 * dans sa collection.
 *
 * <p>Deux principes gouvernent ce service :
 * <ul>
 *   <li><b>Le texte est extrait ici, pas envoyé par le client.</b> On reçoit des
 *       coordonnées (bloc + bornes), on lit le chapitre, on découpe. Le texte cité
 *       correspond donc forcément à ce qui est écrit dans le chapitre.</li>
 *   <li><b>La collection ne dépend pas du chapitre.</b> Le texte est figé à la
 *       capture ; l'ancre n'est consultée que pour « aller au passage ».</li>
 * </ul>
 */
@Service
public class QuoteService {

    private static final int MAX_PAGE_SIZE = 50;

    private final PassageAnnotationRepository annotationRepository;
    private final ChapterService chapterService;
    private final UserService userService;

    public QuoteService(
            PassageAnnotationRepository annotationRepository,
            ChapterService chapterService,
            UserService userService) {
        this.annotationRepository = annotationRepository;
        this.chapterService = chapterService;
        this.userService = userService;
    }

    /**
     * Range un passage dans la collection de l'utilisateur.
     *
     * @param blockIndex  index du bloc dans le chapitre, tel que l'app l'a rendu
     * @param startOffset début de la sélection DANS ce bloc (inclus)
     * @param endOffset   fin de la sélection DANS ce bloc (exclu)
     */
    @Transactional
    public QuoteResponse create(
            String email, Long chapterId, int blockIndex, int startOffset, int endOffset) {
        User user = userService.findByEmail(email);
        Chapter chapter = chapterService.findById(chapterId);

        List<String> blocks = ChapterBlocks.split(chapter.getContent());
        if (blockIndex < 0 || blockIndex >= blocks.size()) {
            // L'app et l'API ont découpé le chapitre différemment, ou le chapitre a
            // changé sous les pieds du lecteur. Dans les deux cas, refuser vaut mieux
            // que citer un passage au hasard.
            throw new InvalidQuoteException("Ce passage n'existe pas dans ce chapitre");
        }

        String block = blocks.get(blockIndex);
        // On borne au lieu de refuser : un décalage d'un caractère en fin de bloc ne
        // justifie pas de perdre la citation que le lecteur vient de choisir.
        int start = Math.clamp(startOffset, 0, block.length());
        int end = Math.clamp(endOffset, 0, block.length());
        if (end <= start) {
            throw new InvalidQuoteException("La sélection est vide");
        }

        String quoted = block.substring(start, end).strip();
        if (quoted.isEmpty()) {
            throw new InvalidQuoteException("La sélection est vide");
        }
        if (quoted.length() > PassageAnnotation.MAX_QUOTED_LENGTH) {
            throw new InvalidQuoteException(
                    "Une citation ne peut pas dépasser "
                            + PassageAnnotation.MAX_QUOTED_LENGTH + " caractères");
        }

        PassageAnnotation saved = annotationRepository.save(PassageAnnotation.quote(
                chapter, user, blockIndex, ChapterBlocks.hash(block), start, end, quoted));

        return new QuoteResponse(
                saved.getId(),
                saved.getQuotedText(),
                chapter.getNovel().getId(),
                chapter.getNovel().getTitle(),
                chapter.getNovel().getCoverUrl(),
                chapter.getId(),
                chapter.getChapterNumber(),
                chapter.getTitle(),
                saved.getCreatedAt());
    }

    /** La collection, la plus récente d'abord. {@code novelId} et {@code search} facultatifs. */
    @Transactional(readOnly = true)
    public Page<QuoteResponse> list(String email, Long novelId, String search, int page, int size) {
        User user = userService.findByEmail(email);

        return annotationRepository
                .findQuotes(
                        user.getId(),
                        novelId,
                        likePattern(search),
                        PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, MAX_PAGE_SIZE)))
                .map(view -> new QuoteResponse(
                        view.getId(),
                        view.getQuotedText(),
                        view.getNovelId(),
                        view.getNovelTitle(),
                        view.getNovelCoverUrl(),
                        view.getChapterId(),
                        view.getChapterNumber(),
                        view.getChapterTitle(),
                        view.getCreatedAt()));
    }

    /** Nombre de citations par roman — filtres de la collection et compteur de fiche. */
    @Transactional(readOnly = true)
    public List<NovelQuoteCountResponse> countsByNovel(String email) {
        User user = userService.findByEmail(email);
        return annotationRepository.countQuotesByNovel(user.getId()).stream()
                .map(row -> new NovelQuoteCountResponse(
                        row.getNovelId(), row.getNovelTitle(), row.getCount()))
                .toList();
    }

    /**
     * Où retrouver la citation aujourd'hui. C'est le seul endroit qui lit le texte
     * intégral d'un chapitre : on ne le fait donc qu'au moment où le lecteur demande
     * réellement à y retourner, jamais pour afficher une liste.
     */
    @Transactional(readOnly = true)
    public QuoteAnchorResponse resolveAnchor(String email, Long quoteId) {
        PassageAnnotation quote = findOwn(email, quoteId);
        Chapter chapter = quote.getChapter();

        ChapterBlocks.Resolution resolution = ChapterBlocks.resolve(
                ChapterBlocks.split(chapter.getContent()),
                quote.getBlockIndex(),
                quote.getTextHash());

        return new QuoteAnchorResponse(
                resolution.alive(),
                resolution.blockIndex(),
                chapter.getId(),
                chapter.getNovel().getId());
    }

    /** Retire une citation de SA collection. */
    @Transactional
    public void delete(String email, Long quoteId) {
        annotationRepository.delete(findOwn(email, quoteId));
    }

    /**
     * Transforme un terme de recherche en motif {@code like}, ou {@code null} si la
     * recherche est vide.
     *
     * <p>Les caractères jokers de SQL sont neutralisés : sans ça, chercher « % »
     * remonterait toute la collection et « _ » remplacerait n'importe quelle lettre —
     * l'utilisateur, lui, cherche ces caractères au sens propre. Le caractère
     * d'échappement est {@code !} plutôt que l'antislash habituel, pour n'avoir à
     * l'échapper ni en Java ni en JPQL.
     */
    private static String likePattern(String search) {
        if (search == null || search.isBlank()) {
            return null;
        }
        String escaped = search.strip()
                .toLowerCase(Locale.ROOT)
                .replace("!", "!!")
                .replace("%", "!%")
                .replace("_", "!_");
        return "%" + escaped + "%";
    }

    private PassageAnnotation findOwn(String email, Long quoteId) {
        User user = userService.findByEmail(email);
        return annotationRepository.findByIdAndUser_Id(quoteId, user.getId())
                .filter(a -> a.getKind() == PassageAnnotation.Kind.QUOTE)
                .orElseThrow(() -> new QuoteNotFoundException(quoteId));
    }
}
