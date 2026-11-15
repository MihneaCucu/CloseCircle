package com.closecircle.api.group;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Maps the {@code groups} table from Flyway migration V1.
 * The entity is named "CircleGroup" because GROUP is a reserved word in JPQL (as in GROUP BY).
 */
@Entity(name = "CircleGroup")
@Table(name = "groups")
public class Group {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "group_id")
    private UUID id;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "created_by", nullable = false, updatable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Group() {
    }

    public Group(String name, UUID createdBy, Instant now) {
        this.name = name;
        this.createdBy = createdBy;
        // Truncated to PostgreSQL's microsecond precision so the returned value equals the stored one.
        this.createdAt = now.truncatedTo(ChronoUnit.MICROS);
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
