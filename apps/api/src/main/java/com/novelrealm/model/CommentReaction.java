package com.novelrealm.model;

import java.time.Instant;

import com.novelrealm.model.CommentMention.SourceKind;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/**
 * Réaction emoji posée par un lecteur sur un commentaire (façon Discord).
 *
 * <p><b>Le pendant, sur les commentaires, des réactions de passage.</b> Un passage
 * portait déjà une réaction ({@link PassageAnnotation#getEmoji()}, une par lecteur) ;
 * ici c'est un COMMENTAIRE qu'on décore, et un lecteur peut en poser plusieurs — 👍
 * <i>et</i> 🔥 — comme sur Discord. L'unicité en base porte donc sur le quadruplet
 * (source, utilisateur, <b>emoji</b>) : poser deux fois le même reste un seul.
 *
 * <p><b>Polymorphe sans clé étrangère sur la source</b>, exactement comme
 * {@link CommentMention} : un commentaire vit dans deux tables selon son emplacement
 * ({@code chapter_comment}, {@code passage_annotation}), aucune FK ne couvre les deux.
 * {@code sourceKind} + {@code sourceId} désignent la ligne. En contrepartie, c'est aux
 * services de supprimer les réactions quand ils suppriment un commentaire de passage
 * (ceux de chapitre ne sont jamais effacés — suppression douce).
 *
 * <p>L'{@code emoji} est libre en base (le bouton « + » ouvre le clavier complet) ;
 * c'est le service qui vérifie que c'en est bien un, pas une chaîne arbitraire.
 */
@Entity
@Table(name = "comment_reaction")
public class CommentReaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_kind", nullable = false, length = 20)
    private SourceKind sourceKind;

    @Column(name = "source_id", nullable = false)
    private Long sourceId;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(nullable = false, length = 16)
    private String emoji;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected CommentReaction() {
    }

    public CommentReaction(SourceKind sourceKind, Long sourceId, User user, String emoji) {
        this.sourceKind = sourceKind;
        this.sourceId = sourceId;
        this.user = user;
        this.emoji = emoji;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public SourceKind getSourceKind() {
        return sourceKind;
    }

    public Long getSourceId() {
        return sourceId;
    }

    public User getUser() {
        return user;
    }

    public String getEmoji() {
        return emoji;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
