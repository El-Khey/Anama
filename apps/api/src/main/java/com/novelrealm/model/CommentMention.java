package com.novelrealm.model;

import java.time.Instant;

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
 * Mention {@code @pseudo} résolue dans un commentaire (issue #45, §2).
 *
 * <p><b>Résolue à la publication, pas au rendu.</b> Le texte du message garde
 * « @pseudo » tel que tapé ; cette ligne, elle, fige QUI était visé — l'identifiant
 * de l'utilisateur — et SOUS QUEL NOM il l'était ({@code handle}). Si la personne
 * change de pseudo ensuite, le lien vers son profil reste juste, et le client
 * retrouve toujours « @{handle} » dans le texte pour le mettre en évidence. Une
 * mention en texte brut seul aurait cassé au premier renommage.
 *
 * <p><b>Polymorphe sans clé étrangère sur la source.</b> Un commentaire vit dans
 * deux tables selon son emplacement (fin de chapitre, passage) : {@code sourceKind}
 * + {@code sourceId} désignent la ligne, aucune FK ne peut couvrir les deux. En
 * contrepartie, c'est aux services de supprimer les mentions quand ils suppriment
 * un commentaire de passage (les commentaires de chapitre, eux, ne sont jamais
 * effacés — suppression douce).
 */
@Entity
@Table(name = "comment_mention")
public class CommentMention {

    /** Dans quelle table vit le commentaire mentionnant. */
    public enum SourceKind {
        /** {@code chapter_comment} — message de fin de chapitre. */
        CHAPTER_COMMENT,
        /** {@code passage_annotation} (kind COMMENT) — message accroché à un passage. */
        PASSAGE_COMMENT,
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_kind", nullable = false, length = 20)
    private SourceKind sourceKind;

    @Column(name = "source_id", nullable = false)
    private Long sourceId;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "mentioned_user_id")
    private User mentioned;

    /** Le pseudo tel qu'il apparaissait dans le texte au moment de la publication. */
    @Column(nullable = false)
    private String handle;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected CommentMention() {
    }

    public CommentMention(SourceKind sourceKind, Long sourceId, User mentioned, String handle) {
        this.sourceKind = sourceKind;
        this.sourceId = sourceId;
        this.mentioned = mentioned;
        this.handle = handle;
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

    public User getMentioned() {
        return mentioned;
    }

    public String getHandle() {
        return handle;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
