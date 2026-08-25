package com.novelrealm.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

import com.novelrealm.model.Genre;

public interface GenreRepository extends JpaRepository<Genre, Long> {
    // Pour le "find-or-create" lors de l'ingestion.
    Optional<Genre> findByName(String name);
}
