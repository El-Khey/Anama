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
 * Notification dans l'app (issue #45, §3) : « quelqu'un a répondu à votre
 * commentaire », « quelqu'un vous a mentionné ».
 *
 * <p>Le schéma est volontairement plus large que ces deux cas : {@link Type}
 * réserve {@code NEW_CHAPTER} pour l'issue #22 (nouveaux chapitres des romans
 * suivis), qui se greffera sur cette même table, ces mêmes endpoints et cette
 * même cloche — sans nouvelle migration.
 *
 * <p><b>L'extrait est figé à la création.</b> Une notification doit rester
 * lisible même si le commentaire d'origine est supprimé ensuite : elle raconte
 * ce qui s'est passé, elle ne suit pas l'état courant. Le lien profond, lui,
 * peut alors tomber sur un fil où le message n'est plus — l'app retombe sur le
 * chapitre, et c'est le bon comportement.
 *
 * <p><b>{@code blockIndex} est l'index d'affichage au moment de l'événement</b>
 * (commentaires de passage uniquement). Une ré-ingestion du chapitre peut le
 * décaler ; le fil demandé à cet index revient alors vide et l'app retombe sur
 * le chapitre. Résoudre l'ancre à chaque affichage de la liste coûterait la
 * lecture du texte de chaque chapitre concerné — pour un cas rare, ce n'est pas
 * le bon échange.
 */
@Entity
@Table(name = "notification")
public class Notification {

    /** Longueur maximale de l'extrait embarqué — de quoi reconnaître le message. */
    public static final int MAX_EXCERPT_LENGTH = 200;

    public enum Type {
        /** Quelqu'un a répondu à un de mes commentaires. */
        COMMENT_REPLY,
        /** Quelqu'un m'a mentionné dans un commentaire. */
        MENTION,
        /** Réservé à l'issue #22 : nouveau chapitre d'un roman suivi. */
        NEW_CHAPTER,
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Destinataire — celui dont la cloche s'allume. */
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Type type;

    /** Qui a déclenché l'événement (null pour les futurs NEW_CHAPTER). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_id")
    private User actor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "novel_id")
    private Novel novel;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chapter_id")
    private Chapter chapter;

    /** Où vit le commentaire visé — voir {@link CommentMention.SourceKind}. */
    @Enumerated(EnumType.STRING)
    @Column(name = "comment_kind", length = 20)
    private CommentMention.SourceKind commentKind;

    /** Identifiant du commentaire dans sa table (pas de FK : table polymorphe). */
    @Column(name = "comment_id")
    private Long commentId;

    /** Index d'affichage du bloc au moment de l'événement (passages uniquement). */
    @Column(name = "block_index")
    private Integer blockIndex;

    @Column(length = MAX_EXCERPT_LENGTH)
    private String excerpt;

    // « is_read » et non « read » : READ est un mot réservé selon les outils SQL,
    // et une colonne booléenne préfixée se lit mieux dans un EXPLAIN.
    @Column(name = "is_read", nullable = false)
    private boolean read;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Notification() {
    }

    private Notification(
            User user,
            Type type,
            User actor,
            Chapter chapter,
            CommentMention.SourceKind commentKind,
            Long commentId,
            Integer blockIndex,
            String excerpt) {
        this.user = user;
        this.type = type;
        this.actor = actor;
        this.novel = chapter.getNovel();
        this.chapter = chapter;
        this.commentKind = commentKind;
        this.commentId = commentId;
        this.blockIndex = blockIndex;
        this.excerpt = excerpt;
    }

    /** « {actor} a répondu à votre commentaire ». */
    public static Notification reply(
            User recipient,
            User actor,
            Chapter chapter,
            CommentMention.SourceKind commentKind,
            Long commentId,
            Integer blockIndex,
            String excerpt) {
        return new Notification(
                recipient, Type.COMMENT_REPLY, actor, chapter,
                commentKind, commentId, blockIndex, excerpt);
    }

    /** « {actor} vous a mentionné ». */
    public static Notification mention(
            User recipient,
            User actor,
            Chapter chapter,
            CommentMention.SourceKind commentKind,
            Long commentId,
            Integer blockIndex,
            String excerpt) {
        return new Notification(
                recipient, Type.MENTION, actor, chapter,
                commentKind, commentId, blockIndex, excerpt);
    }

    public void markRead() {
        this.read = true;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public Type getType() {
        return type;
    }

    public User getActor() {
        return actor;
    }

    public Novel getNovel() {
        return novel;
    }

    public Chapter getChapter() {
        return chapter;
    }

    public CommentMention.SourceKind getCommentKind() {
        return commentKind;
    }

    public Long getCommentId() {
        return commentId;
    }

    public Integer getBlockIndex() {
        return blockIndex;
    }

    public String getExcerpt() {
        return excerpt;
    }

    public boolean isRead() {
        return read;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
