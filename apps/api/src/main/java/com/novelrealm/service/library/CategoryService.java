package com.novelrealm.service.library;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.novelrealm.exception.library.CategoryNameAlreadyUsedException;
import com.novelrealm.exception.library.CategoryNotFoundException;
import com.novelrealm.model.Category;
import com.novelrealm.model.Novel;
import com.novelrealm.model.User;
import com.novelrealm.repository.CategoryRepository;
import com.novelrealm.service.novel.NovelService;
import com.novelrealm.service.user.UserService;

/**
 * Logique métier des étagères personnelles.
 *
 * <p>Comme pour la bibliothèque, l'utilisateur est identifié par son email
 * (principal de session). Toute étagère manipulée est d'abord rechargée via
 * {@link #getOwnedCategory} : on ne touche jamais qu'à une étagère appartenant à
 * l'utilisateur connecté.
 */
@Service
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final UserService userService;
    private final NovelService novelService;

    public CategoryService(
            CategoryRepository categoryRepository,
            UserService userService,
            NovelService novelService) {
        this.categoryRepository = categoryRepository;
        this.userService = userService;
        this.novelService = novelService;
    }

    /** Liste les étagères de l'utilisateur, triées par nom. */
    @Transactional(readOnly = true)
    public List<Category> listForUser(String email) {
        User user = userService.findByEmail(email);
        return categoryRepository.findByUserIdOrderByNameAsc(user.getId());
    }

    /** Consulte une étagère précise de l'utilisateur (avec ses romans). */
    @Transactional(readOnly = true)
    public Category getDetail(String email, Long categoryId) {
        return getOwnedCategory(email, categoryId);
    }

    /**
     * Crée une nouvelle étagère.
     *
     * @throws CategoryNameAlreadyUsedException si l'utilisateur a déjà ce nom.
     */
    @Transactional
    public Category create(String email, String name) {
        User user = userService.findByEmail(email);
        if (categoryRepository.existsByUserIdAndName(user.getId(), name)) {
            throw new CategoryNameAlreadyUsedException(name);
        }
        return categoryRepository.save(new Category(user, name));
    }

    /**
     * Renomme une étagère.
     *
     * @throws CategoryNotFoundException       si elle n'existe pas / pas à lui.
     * @throws CategoryNameAlreadyUsedException si le nouveau nom est déjà pris.
     */
    @Transactional
    public Category rename(String email, Long categoryId, String name) {
        Category category = getOwnedCategory(email, categoryId);

        // On ne vérifie l'unicité que si le nom change réellement (sinon l'étagère
        // « entrerait en collision avec elle-même »).
        if (!category.getName().equals(name)
                && categoryRepository.existsByUserIdAndName(category.getUser().getId(), name)) {
            throw new CategoryNameAlreadyUsedException(name);
        }

        category.setName(name);
        return category; // dirty checking → UPDATE au commit
    }

    /** Supprime une étagère (les liens category_novel partent en cascade). */
    @Transactional
    public void delete(String email, Long categoryId) {
        Category category = getOwnedCategory(email, categoryId);
        categoryRepository.delete(category);
    }

    /**
     * Ajoute un roman à une étagère (sans effet s'il y est déjà — c'est un Set).
     *
     * @throws CategoryNotFoundException si l'étagère n'est pas à l'utilisateur.
     */
    @Transactional
    public Category addNovel(String email, Long categoryId, Long novelId) {
        Category category = getOwnedCategory(email, categoryId);
        Novel novel = novelService.findById(novelId);
        category.addNovel(novel);
        return category;
    }

    /** Retire un roman d'une étagère (sans effet s'il n'y était pas). */
    @Transactional
    public Category removeNovel(String email, Long categoryId, Long novelId) {
        Category category = getOwnedCategory(email, categoryId);
        Novel novel = novelService.findById(novelId);
        category.removeNovel(novel);
        return category;
    }

    /**
     * Charge l'étagère (id + utilisateur courant) ou lève une 404. Un id qui
     * appartient à un autre utilisateur est traité comme « introuvable ».
     */
    private Category getOwnedCategory(String email, Long categoryId) {
        User user = userService.findByEmail(email);
        return categoryRepository.findByIdAndUserId(categoryId, user.getId())
                .orElseThrow(() -> new CategoryNotFoundException(categoryId));
    }
}
