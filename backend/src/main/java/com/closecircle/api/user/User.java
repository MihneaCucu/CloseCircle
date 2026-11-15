package com.closecircle.api.user;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Maps the {@code users} table created by Flyway migration V1. */
@Entity
@Table(name = "users")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "user_id")
    private UUID id;

    @Column(name = "email", nullable = false, length = 320)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Required by JPA. */
    protected User() {
    }

    public User(String email, String passwordHash, String displayName, Instant now) {
        // PostgreSQL stores microseconds; truncating keeps the value we return equal to the stored one.
        Instant timestamp = now.truncatedTo(ChronoUnit.MICROS);
        this.email = email;
        this.passwordHash = passwordHash;
        this.displayName = displayName;
        // Set explicitly: Hibernate would insert NULL and bypass the column's DEFAULT now().
        this.createdAt = timestamp;
        this.updatedAt = timestamp;
    }

    public UUID getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    /** Excludes the password hash so it never ends up in logs. */
    @Override
    public String toString() {
        return "User[id=" + id + "]";
    }
}
