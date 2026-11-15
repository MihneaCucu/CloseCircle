package com.closecircle.api.invitation;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Maps the {@code invitations} table from Flyway migration V1. */
@Entity
@Table(name = "invitations")
public class Invitation {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "invitation_id")
    private UUID id;

    @Column(name = "group_id", nullable = false, updatable = false)
    private UUID groupId;

    @Column(name = "invited_by", nullable = false, updatable = false)
    private UUID invitedBy;

    @Column(name = "invitee_email", nullable = false, length = 320, updatable = false)
    private String inviteeEmail;

    @Column(name = "token_hash", nullable = false, updatable = false)
    private String tokenHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private InvitationStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    /** Must be set exactly when the status is ACCEPTED (check constraint invitations_acceptance_ck). */
    @Column(name = "accepted_at")
    private Instant acceptedAt;

    protected Invitation() {
    }

    public Invitation(UUID groupId, UUID invitedBy, String inviteeEmail, String tokenHash,
            Instant now, Instant expiresAt) {
        this.groupId = groupId;
        this.invitedBy = invitedBy;
        this.inviteeEmail = inviteeEmail;
        this.tokenHash = tokenHash;
        // Set explicitly: Hibernate would insert NULL instead of using the column's DEFAULT 'PENDING'.
        this.status = InvitationStatus.PENDING;
        this.createdAt = now.truncatedTo(ChronoUnit.MICROS);
        this.expiresAt = expiresAt.truncatedTo(ChronoUnit.MICROS);
    }

    public UUID getId() {
        return id;
    }

    public UUID getGroupId() {
        return groupId;
    }

    public UUID getInvitedBy() {
        return invitedBy;
    }

    public String getInviteeEmail() {
        return inviteeEmail;
    }

    public InvitationStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    /** Leaves the token hash out, in case this object is ever logged. */
    @Override
    public String toString() {
        return "Invitation[id=" + id + ", groupId=" + groupId + ", status=" + status + "]";
    }
}
