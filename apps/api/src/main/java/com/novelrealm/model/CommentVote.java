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
 * Vote (pouce vert / pouce rouge) posé par un lecteur sur un commentaire.
 *
 * <p><b>Un seul vote par lecteur et par message</b> — contrairement aux réactions emoji
 * ({@link CommentReaction}), où un lecteur peut en cumuler plusieurs. On est POUR
 * ({@code value = +1}) ou CONTRE ({@code value = -1}) ; le neutre n'existe pas en base,
 * c'est l'absence de ligne. Changer d'avis met à jour la ligne existante ; revoter le
 * même sens la supprime (retour au neutre). L'unicité en base porte donc sur le triplet
 * (source, utilisateur) — sans l'emoji, cette fois.
 *
 * <p><b>Polymorphe sans clé étrangère sur la source</b>, exactement comme
 * {@link CommentReaction} et {@link CommentMention} : un commentaire vit dans deux tables
 * selon son emplacement ({@code chapter_comment}, {@code passage_annotation}), aucune FK
 * ne couvre les deux. {@code sourceKind} + {@code sourceId} désignent la ligne. C'est aux
 * services de supprimer les votes quand ils suppriment vraiment un commentaire.
 */
@Entity
@Table(name = "comment_vote")
public class CommentVote {

    /** Les deux sens d'un vote — jamais zéro : « neutre » = pas de ligne. */
    public static final short UP = 1;
    public static final short DOWN = -1;

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

    @Column(nullable = false)
    private short value;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected CommentVote() {
    }

    public CommentVote(SourceKind sourceKind, Long sourceId, User user, short value) {
        this.sourceKind = sourceKind;
        this.sourceId = sourceId;
        this.user = user;
        this.value = value;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }

    /** Changement d'avis : la même ligne bascule d'un sens à l'autre. */
    public void setValue(short value) {
        this.value = value;
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

    public short getValue() {
        return value;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
