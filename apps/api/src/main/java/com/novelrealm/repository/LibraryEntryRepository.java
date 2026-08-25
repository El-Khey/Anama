package com.novelrealm.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.novelrealm.model.LibraryEntry;
import com.novelrealm.model.LibraryEntryId;

/**
 * Accès aux entrées de bibliothèque.
 *
 * <p>{@link JpaRepository} fournit déjà {@code save()}, {@code findById(id)},
 * {@code delete()} et {@code findAll()}. On ajoute ici uniquement les requêtes
 * dérivées propres à notre besoin : elles « traversent » les relations
 * {@code @ManyToOne} — {@code UserId} = {@code user.id}, {@code NovelId} =
 * {@code novel.id}.
 *
 * <p>{@code @EntityGraph(attributePaths = "novel")} force le chargement du roman
 * EN MÊME TEMPS que l'entrée (un seul SELECT avec jointure). Sans ça, {@code novel}
 * étant {@code LAZY}, y accéder dans le controller — hors transaction — lèverait
 * une {@code LazyInitializationException}.
 */
public interface LibraryEntryRepository extends JpaRepository<LibraryEntry, LibraryEntryId> {

    /** Toutes les entrées d'un utilisateur (roman inclus), les plus récentes d'abord. */
    @EntityGraph(attributePaths = "novel")
    List<LibraryEntry> findByUserIdOrderByAddedAtDesc(Long userId);

    /** L'entrée pour un couple (utilisateur, roman) précis (roman inclus), si elle existe. */
    @EntityGraph(attributePaths = "novel")
    Optional<LibraryEntry> findByUserIdAndNovelId(Long userId, Long novelId);

    /** Vrai si l'utilisateur a déjà ce roman dans sa bibliothèque. */
    boolean existsByUserIdAndNovelId(Long userId, Long novelId);

    /** Nombre de romans suivis par l'utilisateur (stats du profil). */
    long countByUserId(Long userId);
}
