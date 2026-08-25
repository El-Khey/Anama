package com.novelrealm.model;

import java.time.Instant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/**
 * Entrée de bibliothèque : un roman suivi par un utilisateur, avec son statut
 * de lecture. Une ligne = un couple (utilisateur, roman) unique.
 *
 * <p>Clé primaire COMPOSITE {@code (user_id, novel_id)} portée par
 * {@link LibraryEntryId} via {@code @IdClass} : les deux relations
 * {@code @ManyToOne} sont elles-mêmes {@code @Id} (clé « dérivée » des entités
 * liées — pas de colonne id technique séparée).
 */
@Entity
@Table(name = "library_entry")
@IdClass(LibraryEntryId.class)

public class LibraryEntry {

    // Partie 1 de la clé : l'utilisateur propriétaire de cette entrée.
    @Id
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    // Partie 2 de la clé : le roman suivi.
    @Id
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "novel_id")
    private Novel novel;

    // Statut de lecture — stocké en TEXTE (@Enumerated STRING) pour coller au
    // CHECK de la colonne SQL, comme Novel.NovelStatus.
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReadingStatus status;

    // Date d'ajout à la bibliothèque (tri « récemment ajouté »). Non modifiable.
    @Column(nullable = false, updatable = false)
    private Instant addedAt;

    public enum ReadingStatus {
        PLAN_TO_READ,
        READING,
        COMPLETED,
        PAUSED
    }

    protected LibraryEntry() {
    }

    public LibraryEntry(User user, Novel novel, ReadingStatus status) {
        this.user = user;
        this.novel = novel;
        this.status = status;
    }

    @PrePersist
    void onAdd() {
        this.addedAt = Instant.now();
    }

    public User getUser() {
        return user;
    }

    public Novel getNovel() {
        return novel;
    }

    public ReadingStatus getStatus() {
        return status;
    }

    public void setStatus(ReadingStatus status) {
        this.status = status;
    }

    public Instant getAddedAt() {
        return addedAt;
    }
}
