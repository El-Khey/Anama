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
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

/**
 * Progression de lecture d'un utilisateur sur un chapitre : lu ou non, position
 * de reprise, date. Une ligne = un couple (utilisateur, chapitre) unique.
 *
 * <p>Clé primaire COMPOSITE {@code (user_id, chapter_id)} via {@code @IdClass}
 * (même schéma que {@link LibraryEntry}) : les deux {@code @ManyToOne} sont
 * aussi {@code @Id} (clé « dérivée » des entités liées).
 */
@Entity
@Table(name = "chapter_progress")
@IdClass(ChapterProgressId.class)
public class ChapterProgress {

    @Id
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Id
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "chapter_id")
    private Chapter chapter;

    @Column(name = "is_read", nullable = false)
    private boolean read;

    // Position de reprise dans le chapitre (scroll) — 0 par défaut.
    @Column(name = "scroll_position", nullable = false)
    private int scrollPosition;

    // Mise à jour à chaque changement (tri « reprendre », historique).
    @Column(name = "read_at", nullable = false)
    private Instant readAt;

    protected ChapterProgress() {
    }

    public ChapterProgress(User user, Chapter chapter) {
        this.user = user;
        this.chapter = chapter;
    }

    @PrePersist
    @PreUpdate
    void touch() {
        this.readAt = Instant.now();
    }

    public User getUser() {
        return user;
    }

    public Chapter getChapter() {
        return chapter;
    }

    public boolean isRead() {
        return read;
    }

    public void setRead(boolean read) {
        this.read = read;
    }

    public int getScrollPosition() {
        return scrollPosition;
    }

    public void setScrollPosition(int scrollPosition) {
        this.scrollPosition = scrollPosition;
    }

    public Instant getReadAt() {
        return readAt;
    }
}
