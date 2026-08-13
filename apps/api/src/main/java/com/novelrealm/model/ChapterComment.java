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
 * Message laissé en fin de chapitre (#41). À la différence de {@link Review}, qui
 * porte une note et reste unique par couple (utilisateur, roman), un lecteur peut
 * écrire autant de commentaires qu'il veut sur un chapitre.
 *
 * <p><b>Un seul niveau de réponse.</b> {@code parent} est soit {@code null} (le
 * message ouvre un fil), soit un message racine — jamais une réponse. C'est le
 * service qui garantit l'invariant en re-rattachant une réponse de réponse à la
 * racine du fil : au-delà d'un niveau, l'indentation devient illisible sur un
 * téléphone.
 *
 * <p><b>Suppression douce.</b> On ne retire jamais la ligne : les réponses
 * accrochées dessus perdraient leur point d'attache. Un message supprimé qui a
 * encore des réponses vivantes reste affiché comme une pierre tombale ; sans
 * réponse, il disparaît simplement de la liste.
 */
@Entity
@Table(name = "chapter_comment")
public class ChapterComment {

    /** Longueur maximale d'un message (garde-fou, aligné sur {@link Review}). */
    public static final int MAX_BODY_LENGTH = 2_000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "chapter_id")
    private Chapter chapter;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    // null = message racine ; sinon TOUJOURS un message racine (jamais une réponse).
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private ChapterComment parent;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String body;

    // ── GIF joint (issue #45, §5) ────────────────────────────────────────────
    // Deux URL Tenor, jamais un fichier chez nous : l'animée, et l'image figée
    // affichée par défaut (l'animation ne démarre qu'à la demande). Nullables :
    // la plupart des messages restent du texte seul. Quand gif_url est posée,
    // body PEUT être vide — mais jamais les deux à la fois (règle du service).
    @Column(name = "gif_url", length = 500)
    private String gifUrl;

    @Column(name = "gif_preview_url", length = 500)
    private String gifPreviewUrl;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // Non null → message supprimé par son auteur. Voir le commentaire de classe.
    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected ChapterComment() {
    }

    public ChapterComment(Chapter chapter, User user, ChapterComment parent, String body) {
        this.chapter = chapter;
        this.user = user;
        this.parent = parent;
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

    /** Joint un GIF (URLs déjà validées par le service — Tenor uniquement). */
    public void attachGif(String gifUrl, String gifPreviewUrl) {
        this.gifUrl = gifUrl;
        this.gifPreviewUrl = gifPreviewUrl;
    }

    /** Marque le message comme supprimé sans effacer la ligne (voir classe). */
    public void softDelete() {
        this.deletedAt = Instant.now();
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public boolean isRoot() {
        return parent == null;
    }

    public Long getId() {
        return id;
    }

    public Chapter getChapter() {
        return chapter;
    }

    public User getUser() {
        return user;
    }

    public ChapterComment getParent() {
        return parent;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public String getGifUrl() {
        return gifUrl;
    }

    public String getGifPreviewUrl() {
        return gifPreviewUrl;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }
}
