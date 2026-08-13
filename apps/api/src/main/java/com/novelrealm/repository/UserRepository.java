package com.novelrealm.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.novelrealm.model.User;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    /**
     * Autocomplétion des mentions (issue #45, §2) : huit résultats au plus, tri
     * alphabétique. « Contient » et non « commence par » — on cherche quelqu'un
     * dont on se rappelle un bout du pseudo, pas forcément le début.
     */
    List<User> findTop8ByPseudoContainingIgnoreCaseOrderByPseudoAsc(String pseudo);
}
