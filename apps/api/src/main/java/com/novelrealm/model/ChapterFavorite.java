package com.novelrealm.model;

import java.time.Instant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/**
 * Chapitre mis en favori (marque-page) par un utilisateur. Une ligne = un couple
 * (utilisateur, chapitre) unique.
 *
 * <p>Clé primaire COMPOSITE {@code (user_id, chapter_id)} via {@code @IdClass}
 * (même schéma que {@link ChapterProgress} / {@link LibraryEntry}) : les deux
 * {@code @ManyToOne} sont aussi {@code @Id} (clé « dérivée » des entités liées).
 */
@Entity
@Table(name = "chapter_favorite")
@IdClass(ChapterFavoriteId.class)
public class ChapterFavorite {

    @Id
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Id
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "chapter_id")
    private Chapter chapter;

    // Date de mise en favori (tri « ajoutés récemment » côté marque-pages).
    @Column(name = "favorited_at", nullable = false, updatable = false)
    private Instant favoritedAt;

    protected ChapterFavorite() {
    }

    public ChapterFavorite(User user, Chapter chapter) {
        this.user = user;
        this.chapter = chapter;
    }

    @PrePersist
    void onAdd() {
        this.favoritedAt = Instant.now();
    }

    public User getUser() {
        return user;
    }

    public Chapter getChapter() {
        return chapter;
    }

    public Instant getFavoritedAt() {
        return favoritedAt;
    }
}
