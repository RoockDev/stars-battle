package com.starsbattle.characters.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * A playable Star Wars character. Stats (hp/baseHp/attack/levelRequired) are
 * seeded verbatim from the source's roster (see V3__characters_roster.sql)
 * and never mutated at runtime — only a Battle's current-hp counters change.
 */
@Entity
@Table(name = "characters")
public class Character {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private Integer hp;

    @Column(name = "base_hp", nullable = false)
    private Integer baseHp;

    @Column(nullable = false)
    private Integer attack;

    @Column(name = "level_required", nullable = false)
    private Integer levelRequired;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private Instant updatedAt;

    protected Character() {
        // JPA
    }

    public Character(String name, Integer hp, Integer baseHp, Integer attack, Integer levelRequired) {
        this.name = name;
        this.hp = hp;
        this.baseHp = baseHp;
        this.attack = attack;
        this.levelRequired = levelRequired;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public Integer getHp() {
        return hp;
    }

    public Integer getBaseHp() {
        return baseHp;
    }

    public Integer getAttack() {
        return attack;
    }

    public Integer getLevelRequired() {
        return levelRequired;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
