package com.closecircle.api.group;

import java.io.Serializable;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

/** One user's membership in one group. The primary key is the pair (group_id, user_id). */
@Entity
@Table(name = "group_members")
public class GroupMember {
    @EmbeddedId
    private Id id;

    // Stored as the enum's name ("OWNER"), matching the column's check constraint.
    // Set explicitly: the column defaults to MEMBER, which is wrong for a group's creator.
    @Enumerated(EnumType.STRING)
    @Column(name = "member_role", nullable = false, length = 20)
    private MemberRole role;

    @Column(name = "joined_at", nullable = false, updatable = false)
    private Instant joinedAt;

    protected GroupMember() {
    }

    public GroupMember(UUID groupId, UUID userId, MemberRole role, Instant now) {
        this.id = new Id(groupId, userId);
        this.role = role;
        this.joinedAt = now.truncatedTo(ChronoUnit.MICROS);
    }

    public UUID getGroupId() {
        return id.groupId;
    }

    public UUID getUserId() {
        return id.userId;
    }

    public MemberRole getRole() {
        return role;
    }

    public Instant getJoinedAt() {
        return joinedAt;
    }

    /** Composite primary key. JPA requires equals/hashCode on it to identify the row. */
    @Embeddable
    public static class Id implements Serializable {
        @Column(name = "group_id")
        private UUID groupId;

        @Column(name = "user_id")
        private UUID userId;

        protected Id() {
        }

        public Id(UUID groupId, UUID userId) {
            this.groupId = groupId;
            this.userId = userId;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Id that
                    && Objects.equals(groupId, that.groupId)
                    && Objects.equals(userId, that.userId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(groupId, userId);
        }
    }
}
