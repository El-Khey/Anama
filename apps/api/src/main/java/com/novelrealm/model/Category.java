package com.novelrealm.model;

import java.util.HashSet;
import java.util.Set;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * Étagère personnelle : un regroupement de romans nommé, propre à un utilisateur
 * (« À lire », « Favoris », « Xianxia »…).
 *
 * <p>Contrainte d'unicité {@code (user_id, name)} : un même utilisateur ne peut
 * pas avoir deux étagères du même nom (déclarée aussi au schéma SQL).
 *
 * <p>Relation M:N vers les romans via la table de jointure {@code category_novel}.
 * {@code Category} est le côté <em>propriétaire</em> de la relation : c'est lui
 * qui porte la {@code @JoinTable}, donc ajouter/retirer un roman se fait sur son
 * {@code Set<Novel>}.
 */
@Entity
@Table(name = "categories", uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "name"}))
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Le propriétaire de l'étagère.
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(nullable = false, length = 100)
    private String name;

    // Les romans rangés dans cette étagère (M:N, table category_novel).
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "category_novel",
            joinColumns = @JoinColumn(name = "category_id"),
            inverseJoinColumns = @JoinColumn(name = "novel_id"))
    private Set<Novel> novels = new HashSet<>();

    protected Category() {
    }

    public Category(User user, String name) {
        this.user = user;
        this.name = name;
    }

    /** Ajoute un roman à l'étagère (sans doublon — c'est un Set). */
    public void addNovel(Novel novel) {
        novels.add(novel);
    }

    /** Retire un roman de l'étagère (sans effet s'il n'y était pas). */
    public void removeNovel(Novel novel) {
        novels.remove(novel);
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Set<Novel> getNovels() {
        return novels;
    }
}
