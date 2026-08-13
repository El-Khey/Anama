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
 * Annotation accrochée à un <b>passage</b> d'un chapitre (#41, §2 et §3).
 *
 * <p>Une seule table pour quatre usages ({@link Kind}) parce qu'ils partagent
 * exactement la même chose : l'ancre. Citer, réagir, surligner ou commenter un
 * passage, c'est toujours désigner le même point du texte — seul ce qu'on y accroche
 * change. Aujourd'hui seul {@link Kind#QUOTE} est produit ; les trois autres valeurs
 * sont posées d'avance pour ne pas avoir à migrer la table quand ils arriveront.
 *
 * <p><b>L'ancre = {@code blockIndex} + {@code textHash}.</b> L'index seul se décale
 * dès qu'un chapitre est ré-ingéré avec un bloc de plus ; l'empreinte permet de
 * retrouver le bloc ailleurs, ou de constater qu'il a disparu. Voir
 * {@code ChapterBlocks#resolve}.
 *
 * <p><b>{@code quotedText} est figé à la capture</b>, et c'est délibéré : la
 * collection de citations doit rester lisible même si le chapitre change ou
 * disparaît. L'ancre n'est qu'un bonus — elle sert le bouton « aller au passage »,
 * pas l'affichage.
 */
@Entity
@Table(name = "passage_annotation")
public class PassageAnnotation {

    /** Longueur maximale d'un passage cité — une citation, pas un chapitre recopié. */
    public static final int MAX_QUOTED_LENGTH = 500;

    /**
     * Longueur maximale d'un commentaire de passage — moitié moins qu'un commentaire de
     * fin de chapitre ({@code ChapterComment.MAX_BODY_LENGTH}).
     *
     * <p>Ce n'est pas de l'avarice : ces messages s'affichent dans un panneau accroché à
     * une seule ligne du texte. On réagit à une réplique, on ne rédige pas une analyse —
     * celle-ci a sa place en fin de chapitre.
     */
    public static final int MAX_BODY_LENGTH = 1_000;

    public enum Kind {
        /** Un emoji posé sur un passage. */
        REACTION,
        /** Un message public accroché à un passage. */
        COMMENT,
        /** Un surlignage privé. */
        HIGHLIGHT,
        /** Une citation rangée dans la collection personnelle. */
        QUOTE,
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "chapter_id")
    private Chapter chapter;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    // ── L'ancre ───────────────────────────────────────────────────────────────
    @Column(name = "block_index", nullable = false)
    private int blockIndex;

    @Column(name = "text_hash", nullable = false, length = 32)
    private String textHash;

    /** Bornes de la sélection DANS le bloc — pour citer une phrase, pas tout le bloc. */
    @Column(name = "start_offset", nullable = false)
    private int startOffset;

    @Column(name = "end_offset", nullable = false)
    private int endOffset;

    // ── Ce qu'on accroche à l'ancre ───────────────────────────────────────────
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Kind kind;

    @Column(name = "quoted_text", columnDefinition = "TEXT")
    private String quotedText;

    @Column(length = 16)
    private String emoji;

    @Column(columnDefinition = "TEXT")
    private String body;

    @Column(name = "is_private", nullable = false)
    private boolean isPrivate = true;

    @Column(name = "is_spoiler", nullable = false)
    private boolean isSpoiler = false;

    // ── GIF joint (issue #45, §5 — commentaires uniquement) ──────────────────
    // Mêmes règles qu'en fin de chapitre : deux URL du fournisseur, l'image figée par
    // défaut, l'animation à la demande. body PEUT être vide si gif_url est posée.
    @Column(name = "gif_url", length = 500)
    private String gifUrl;

    @Column(name = "gif_preview_url", length = 500)
    private String gifPreviewUrl;

    /**
     * Message auquel celui-ci répond, ou {@code null} s'il ouvre le fil.
     *
     * <p><b>Un seul niveau</b>, comme en fin de chapitre : au-delà, l'indentation
     * devient illisible sur un téléphone. Une réponse à une réponse est re-rattachée
     * au même fil racine par le service, avec une mention {@code @pseudo}.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private PassageAnnotation parent;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected PassageAnnotation() {
    }

    /** Fabrique la seule forme produite aujourd'hui : une citation privée. */
    public static PassageAnnotation quote(
            Chapter chapter,
            User user,
            int blockIndex,
            String textHash,
            int startOffset,
            int endOffset,
            String quotedText) {
        PassageAnnotation annotation = new PassageAnnotation();
        annotation.chapter = chapter;
        annotation.user = user;
        annotation.blockIndex = blockIndex;
        annotation.textHash = textHash;
        annotation.startOffset = startOffset;
        annotation.endOffset = endOffset;
        annotation.kind = Kind.QUOTE;
        annotation.quotedText = quotedText;
        annotation.isPrivate = true;
        return annotation;
    }

    /**
     * Un message public accroché à un bloc (#41, §4).
     *
     * <p>Les bornes intra-bloc restent à zéro : on commente <b>le bloc</b>, pas une
     * sélection dedans. Citer vise une phrase précise parce qu'on la recopie ; réagir
     * vise le moment, et le moment c'est le paragraphe.
     */
    public static PassageAnnotation comment(
            Chapter chapter,
            User user,
            int blockIndex,
            String textHash,
            String body,
            boolean isSpoiler,
            PassageAnnotation parent) {
        PassageAnnotation annotation = new PassageAnnotation();
        annotation.parent = parent;
        annotation.chapter = chapter;
        annotation.user = user;
        annotation.blockIndex = blockIndex;
        annotation.textHash = textHash;
        annotation.kind = Kind.COMMENT;
        annotation.body = body;
        annotation.isSpoiler = isSpoiler;
        annotation.isPrivate = false;
        return annotation;
    }

    /** Un emoji posé sur un bloc (#41, §4). Public par nature : il sert à l'agrégat. */
    public static PassageAnnotation reaction(
            Chapter chapter,
            User user,
            int blockIndex,
            String textHash,
            String emoji) {
        PassageAnnotation annotation = new PassageAnnotation();
        annotation.chapter = chapter;
        annotation.user = user;
        annotation.blockIndex = blockIndex;
        annotation.textHash = textHash;
        annotation.kind = Kind.REACTION;
        annotation.emoji = emoji;
        annotation.isPrivate = false;
        return annotation;
    }

    /** Change l'emoji d'une réaction existante — voir {@code PassageService#react}. */
    public void changeEmoji(String emoji) {
        this.emoji = emoji;
    }

    /** Joint un GIF à un commentaire (URLs déjà validées par le service). */
    public void attachGif(String gifUrl, String gifPreviewUrl) {
        this.gifUrl = gifUrl;
        this.gifPreviewUrl = gifPreviewUrl;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
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

    public int getBlockIndex() {
        return blockIndex;
    }

    public String getTextHash() {
        return textHash;
    }

    public int getStartOffset() {
        return startOffset;
    }

    public int getEndOffset() {
        return endOffset;
    }

    public Kind getKind() {
        return kind;
    }

    public String getQuotedText() {
        return quotedText;
    }

    public String getEmoji() {
        return emoji;
    }

    public String getBody() {
        return body;
    }

    public boolean isPrivate() {
        return isPrivate;
    }

    public boolean isSpoiler() {
        return isSpoiler;
    }

    public String getGifUrl() {
        return gifUrl;
    }

    public String getGifPreviewUrl() {
        return gifPreviewUrl;
    }

    public PassageAnnotation getParent() {
        return parent;
    }

    /** Un message racine ouvre le fil ; les autres sont des réponses. */
    public boolean isRoot() {
        return parent == null;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
