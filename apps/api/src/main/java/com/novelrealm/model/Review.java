package com.novelrealm.model;

import java.time.Instant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

/**
 * Avis d'un utilisateur sur un roman : une note de 1 à 5 étoiles et un
 * commentaire facultatif.
 *
 * <p>Contrairement à {@link ChapterFavorite} ou {@link LibraryEntry}, l'avis a
 * sa PROPRE clé technique : il est modifiable et se référence tout seul. Le
 * couple {@code (user, novel)} reste unique en base — on modifie son avis, on
 * n'en accumule pas.
 */
@Entity
@Table(name = "review")
public class Review {

    /** Longueur maximale du commentaire (garde-fou, aligné sur le front). */
    public static final int MAX_BODY_LENGTH = 2_000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "novel_id")
    private Novel novel;

    @Column(nullable = false)
    private short rating;

    // Commentaire libre — null quand l'utilisateur ne met qu'une note.
    @Column(columnDefinition = "TEXT")
    private String body;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Review() {
    }

    public Review(User user, Novel novel, short rating, String body) {
        this.user = user;
        this.novel = novel;
        this.rating = rating;
        this.body = body;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public Novel getNovel() {
        return novel;
    }

    public short getRating() {
        return rating;
    }

    public void setRating(short rating) {
        this.rating = rating;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
