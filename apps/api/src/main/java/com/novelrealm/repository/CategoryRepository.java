package com.novelrealm.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.novelrealm.model.Category;

/**
 * Accès aux étagères personnelles.
 *
 * <p>Les requêtes qui doivent renvoyer les romans de l'étagère utilisent
 * {@code @EntityGraph(attributePaths = "novels")} pour les charger dans la foulée
 * (la relation est {@code LAZY}) et éviter une {@code LazyInitializationException}
 * lors du mapping en DTO, hors transaction.
 */
public interface CategoryRepository extends JpaRepository<Category, Long> {

    /** Les étagères d'un utilisateur (romans inclus), triées par nom. */
    @EntityGraph(attributePaths = "novels")
    List<Category> findByUserIdOrderByNameAsc(Long userId);

    /** Une étagère précise SI elle appartient bien à l'utilisateur (romans inclus). */
    @EntityGraph(attributePaths = "novels")
    Optional<Category> findByIdAndUserId(Long id, Long userId);

    /** Vrai si l'utilisateur a déjà une étagère de ce nom (contrainte d'unicité). */
    boolean existsByUserIdAndName(Long userId, String name);
}
