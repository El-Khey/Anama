package com.novelrealm.model;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "novels")
public class Novel {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Identifiant source (ex. "shadow-slave") — clé d'idempotence du scraping.
    @Column(unique = true)
    private String slug;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String author;

    @Column(nullable = false)
    private String description;

    @Column(nullable = true)
    private String coverImageUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NovelStatus status;

    // Contenu adulte : pilote le filtrage lors de la découverte automatique
    // (issue ingestion V2). Défaut false ; nullable=false en base.
    @Column(name = "is_nsfw", nullable = false)
    private boolean nsfw = false;

    // ── Identité SOURCE + signaux de fraîcheur (ingestion V2 chikari) ──────────
    // `source`/`sourceId` forment la clé d'idempotence de l'upsert (index unique
    // partiel novels_source_id_uq). Les trois `source*Count/Number/ChapterAt`
    // sont les signaux qui permettent de savoir « rien n'a changé » sans tirer
    // aucun chapitre. Tous nullable : les romans hérités (ère lightnovelworld)
    // n'ont pas d'identité source. Voir docs/INGESTION_V2.md.
    @Column(name = "source")
    private String source;

    @Column(name = "source_id")
    private Long sourceId;

    @Column(name = "source_slug")
    private String sourceSlug;

    @Column(name = "source_chapter_count")
    private Integer sourceChapterCount;

    @Column(name = "source_latest_number")
    private java.math.BigDecimal sourceLatestNumber;

    @Column(name = "source_last_chapter_at")
    private Instant sourceLastChapterAt;

    @Column(name = "last_synced_at")
    private Instant lastSyncedAt;

    // Relation M:N vers les genres (table de jointure novel_genre).
    @ManyToMany
    @JoinTable(
            name = "novel_genre",
            joinColumns = @JoinColumn(name = "novel_id"), 
            inverseJoinColumns = @JoinColumn(name = "genre_id"))
    private Set<Genre> genres = new HashSet<>();

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    public enum NovelStatus {
        ONGOING,
        COMPLETED
    }

    protected Novel() {}

    public Novel(String slug, String title, String author, String description, String coverImageUrl,
            NovelStatus status) {
        this.slug = slug;
        this.title = title;
        this.author = author;
        this.description = description;
        this.coverImageUrl = coverImageUrl;
        this.status = status;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getSlug() {
        return slug;
    }

    public String getTitle() {
        return title;
    }

    public String getAuthor() {
        return author;
    }

    public String getDescription() {
        return description;
    }

    public String getCoverUrl() {
        return coverImageUrl;
    }

    public NovelStatus getStatus() {
        return status;
    }

    public Set<Genre> getGenres() {
        return genres;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setSlug(String slug) {
        this.slug = slug;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setCoverImageUrl(String coverImageUrl) {
        this.coverImageUrl = coverImageUrl;
    }

    public void setStatus(NovelStatus status) {
        this.status = status;
    }

    public void setGenres(Set<Genre> genres) {
        this.genres = genres;
    }

    // ── Contenu adulte ─────────────────────────────────────────────────────────
    public boolean isNsfw() {
        return nsfw;
    }

    public void setNsfw(boolean nsfw) {
        this.nsfw = nsfw;
    }

    // ── Identité source + fraîcheur (ingestion V2) ─────────────────────────────
    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public Long getSourceId() {
        return sourceId;
    }

    public void setSourceId(Long sourceId) {
        this.sourceId = sourceId;
    }

    public String getSourceSlug() {
        return sourceSlug;
    }

    public void setSourceSlug(String sourceSlug) {
        this.sourceSlug = sourceSlug;
    }

    public Integer getSourceChapterCount() {
        return sourceChapterCount;
    }

    public void setSourceChapterCount(Integer sourceChapterCount) {
        this.sourceChapterCount = sourceChapterCount;
    }

    public java.math.BigDecimal getSourceLatestNumber() {
        return sourceLatestNumber;
    }

    public void setSourceLatestNumber(java.math.BigDecimal sourceLatestNumber) {
        this.sourceLatestNumber = sourceLatestNumber;
    }

    public Instant getSourceLastChapterAt() {
        return sourceLastChapterAt;
    }

    public void setSourceLastChapterAt(Instant sourceLastChapterAt) {
        this.sourceLastChapterAt = sourceLastChapterAt;
    }

    public Instant getLastSyncedAt() {
        return lastSyncedAt;
    }

    public void setLastSyncedAt(Instant lastSyncedAt) {
        this.lastSyncedAt = lastSyncedAt;
    }
}
