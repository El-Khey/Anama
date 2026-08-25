package com.novelrealm.service.library;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.novelrealm.exception.library.LibraryEntryAlreadyExistsException;
import com.novelrealm.exception.library.LibraryEntryNotFoundException;
import com.novelrealm.model.LibraryEntry;
import com.novelrealm.model.LibraryEntry.ReadingStatus;
import com.novelrealm.model.Novel;
import com.novelrealm.model.User;
import com.novelrealm.repository.CategoryRepository;
import com.novelrealm.repository.LibraryEntryRepository;
import com.novelrealm.service.novel.NovelService;
import com.novelrealm.service.user.UserService;

/**
 * Logique métier de la bibliothèque personnelle.
 *
 * <p>On identifie toujours l'utilisateur par son email (le « principal » posé au
 * login, cf. {@code authentication.getName()}). Le service s'appuie sur
 * {@link UserService} et {@link NovelService} pour recharger les entités liées
 * — ce qui garantit au passage que l'utilisateur et le roman existent bien
 * (sinon leurs exceptions 404 respectives sont levées).
 */
@Service
public class LibraryEntryService {

    private final LibraryEntryRepository libraryEntryRepository;
    private final CategoryRepository categoryRepository;
    private final UserService userService;
    private final NovelService novelService;

    public LibraryEntryService(
            LibraryEntryRepository libraryEntryRepository,
            CategoryRepository categoryRepository,
            UserService userService,
            NovelService novelService) {
        this.libraryEntryRepository = libraryEntryRepository;
        this.categoryRepository = categoryRepository;
        this.userService = userService;
        this.novelService = novelService;
    }

    /** Liste la bibliothèque de l'utilisateur, la plus récente en tête. */
    @Transactional(readOnly = true)
    public List<LibraryEntry> listForUser(String email) {
        User user = userService.findByEmail(email);
        return libraryEntryRepository.findByUserIdOrderByAddedAtDesc(user.getId());
    }

    /**
     * Ajoute un roman à la bibliothèque de l'utilisateur.
     *
     * @param status statut initial, ou {@code null} pour {@code PLAN_TO_READ}.
     * @throws LibraryEntryAlreadyExistsException si le roman y est déjà.
     */
    @Transactional
    public LibraryEntry add(String email, Long novelId, ReadingStatus status) {
        User user = userService.findByEmail(email);
        Novel novel = novelService.findById(novelId);

        if (libraryEntryRepository.existsByUserIdAndNovelId(user.getId(), novel.getId())) {
            throw new LibraryEntryAlreadyExistsException(novelId);
        }

        ReadingStatus initialStatus = (status != null) ? status : ReadingStatus.PLAN_TO_READ;
        return libraryEntryRepository.save(new LibraryEntry(user, novel, initialStatus));
    }

    /**
     * Change le statut de lecture d'une entrée existante.
     *
     * @throws LibraryEntryNotFoundException si le roman n'est pas dans la biblio.
     */
    @Transactional
    public LibraryEntry updateStatus(String email, Long novelId, ReadingStatus status) {
        LibraryEntry entry = getOwnedEntry(email, novelId);
        entry.setStatus(status);
        return entry; // @Transactional → flush automatique du changement (dirty checking)
    }

    /**
     * Retire un roman de la bibliothèque de l'utilisateur — et de <b>toutes</b> ses
     * étagères au passage.
     *
     * <p>Le nettoyage des étagères n'est pas un supplément : une étagère ne range que des
     * romans suivis. Sans lui, le roman disparaissait de la bibliothèque mais restait
     * rangé dans ses étagères, où l'app continuait de l'afficher — et le rechargement
     * suivant le renvoyait, puisque la table de jointure le contenait toujours.
     *
     * <p>C'est {@link com.novelrealm.model.Category} qui porte la {@code @JoinTable} :
     * on agit donc sur son {@code Set<Novel>}, et le {@code DELETE} dans
     * {@code category_novel} part au flush de cette transaction. Suppression par
     * identifiant plutôt que par instance, pour ne pas dépendre de l'{@code equals}
     * de {@code Novel}.
     *
     * @throws LibraryEntryNotFoundException si le roman n'y est pas.
     */
    @Transactional
    public void remove(String email, Long novelId) {
        LibraryEntry entry = getOwnedEntry(email, novelId);

        categoryRepository.findByUserIdOrderByNameAsc(entry.getUser().getId())
                .forEach(category -> category.getNovels()
                        .removeIf(novel -> novel.getId().equals(novelId)));

        libraryEntryRepository.delete(entry);
    }

    /**
     * Charge l'entrée (utilisateur courant, roman) ou lève une 404. Sert de
     * garde-fou : on ne touche jamais qu'à une entrée qui appartient bien à
     * l'utilisateur connecté.
     */
    private LibraryEntry getOwnedEntry(String email, Long novelId) {
        User user = userService.findByEmail(email);
        return libraryEntryRepository.findByUserIdAndNovelId(user.getId(), novelId)
                .orElseThrow(() -> new LibraryEntryNotFoundException(novelId));
    }
}
