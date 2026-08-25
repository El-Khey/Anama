package com.novelrealm.service.passage;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.novelrealm.dto.passage.NovelQuoteCountResponse;
import com.novelrealm.dto.passage.QuoteAnchorResponse;
import com.novelrealm.dto.passage.QuoteResponse;
import com.novelrealm.exception.passage.InvalidQuoteException;
import com.novelrealm.exception.passage.QuoteNotFoundException;
import com.novelrealm.model.Chapter;
import com.novelrealm.model.PassageAnnotation;
import com.novelrealm.model.User;
import com.novelrealm.repository.PassageAnnotationRepository;
import com.novelrealm.service.chapter.ChapterBlocks;
import com.novelrealm.service.chapter.ChapterService;
import com.novelrealm.service.user.UserService;

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

    /** Valeur de {@code sort} qui inverse l'ordre ; toute autre donne « récentes d'abord ». */
    private static final String OLDEST_FIRST = "oldest";

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

    /**
     * La collection. Tous les critères sont facultatifs et se combinent :
     * {@code novelId} restreint à un roman, {@code search} cherche dans le texte cité,
     * {@code sort} vaut {@code "recent"} (défaut) ou {@code "oldest"}, {@code days}
     * limite aux N derniers jours ({@code 0} = depuis toujours).
     *
     * <p>Tout est appliqué en base, jamais sur la page reçue : la collection est
     * paginée, trier ou filtrer vingt lignes déjà choisies ne trierait que ces
     * vingt-là.
     */
    @Transactional(readOnly = true)
    public Page<QuoteResponse> list(
            String email, Long novelId, String search, String sort, int days, int page, int size) {
        User user = userService.findByEmail(email);

        return annotationRepository
                .findQuotes(
                        user.getId(),
                        novelId,
                        likePattern(search),
                        since(days),
                        PageRequest.of(
                                Math.max(page, 0),
                                Math.clamp(size, 1, MAX_PAGE_SIZE),
                                order(sort)))
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
     * Ordre demandé, avec l'identifiant en arbitre.
     *
     * <p>Deux citations capturées dans la même seconde auraient sinon un ordre
     * indéterminé, susceptible de changer d'une requête à l'autre — et en pagination,
     * cela se voit : une citation apparaît deux fois pendant qu'une autre disparaît.
     * L'identifiant est unique et suit la même chronologie, il départage sans jamais
     * contredire la date.
     */
    private static Sort order(String sort) {
        Sort.Direction direction = OLDEST_FIRST.equalsIgnoreCase(sort)
                ? Sort.Direction.ASC
                : Sort.Direction.DESC;
        return Sort.by(direction, "createdAt", "id");
    }

    /**
     * Borne basse de la période demandée.
     *
     * <p>« Depuis toujours » renvoie {@link Instant#EPOCH} et non {@code null} : la
     * requête compare toujours à une date réelle, ce qui lui évite d'avoir à gérer un
     * paramètre nul non typé — la cause du bug {@code lower(bytea)} rencontré sur la
     * recherche.
     */
    private static Instant since(int days) {
        return days > 0 ? Instant.now().minus(Duration.ofDays(days)) : Instant.EPOCH;
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
