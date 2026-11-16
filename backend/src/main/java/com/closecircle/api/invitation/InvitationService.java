package com.closecircle.api.invitation;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

import com.closecircle.api.error.ApiProblem;
import com.closecircle.api.group.GroupAccess;
import com.closecircle.api.pagination.KeysetCursor;
import com.closecircle.api.pagination.PageResponse;
import com.closecircle.api.user.User;
import com.closecircle.api.user.UserNotFoundException;
import com.closecircle.api.user.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InvitationService {
    /** How long an invitation can be answered. Matches the contract's example (created + 7 days). */
    static final Duration LIFETIME = Duration.ofDays(7);

    private final InvitationRepository invitations;
    private final UserRepository users;
    private final GroupAccess access;
    private final JdbcTemplate jdbc;

    public InvitationService(InvitationRepository invitations, UserRepository users, GroupAccess access,
            JdbcTemplate jdbc) {
        this.invitations = invitations;
        this.users = users;
        this.access = access;
        this.jdbc = jdbc;
    }

    /** An owner or admin invites someone by email. The invitation stays PENDING until it is answered or expires. */
    @Transactional
    public InvitationResponse create(UUID groupId, UUID inviterId, CreateInvitationRequest request) {
        access.requireOwnerOrAdmin(groupId, inviterId);
        String email = request.email();
        if (isMemberEmail(groupId, email)) {
            throw new AlreadyMemberException();
        }
        Instant now = Instant.now();

        // Nothing changes a row's status when it expires, so an old PENDING invitation could still block a new one.
        // Mark this person's expired ones first; then the unique index only sees invitations that are really pending.
        jdbc.update("""
                UPDATE invitations SET status = 'EXPIRED'
                WHERE group_id = ? AND lower(invitee_email) = lower(?) AND status = 'PENDING' AND expires_at <= ?
                """, groupId, email, utc(now));

        var invitation = new Invitation(groupId, inviterId, email, InvitationTokens.hash(InvitationTokens.newToken()),
                now, now.plus(LIFETIME));
        try {
            invitations.saveAndFlush(invitation);
        } catch (DataIntegrityViolationException e) {
            // V4's unique index: this email already has a pending invitation to this group (also catches races).
            throw new InvitationAlreadyPendingException();
        }
        return new InvitationResponse(invitation.getId(), groupId, email, InvitationStatus.PENDING,
                invitation.getCreatedAt(), invitation.getExpiresAt());
    }

    /** The group's invitations for its owner/admins, newest first. Shows whether each one is still pending. */
    @Transactional(readOnly = true)
    public PageResponse<InvitationResponse> listForGroup(UUID groupId, UUID callerId, Integer limit, String cursor) {
        access.requireOwnerOrAdmin(groupId, callerId);
        Instant now = Instant.now();
        // A PENDING row past its expiry is reported as EXPIRED, since no background job updates it.
        return page("""
                SELECT i.invitation_id, i.group_id, i.invitee_email, i.created_at, i.expires_at,
                       CASE WHEN i.status = 'PENDING' AND i.expires_at <= ? THEN 'EXPIRED' ELSE i.status END AS status
                FROM invitations i
                WHERE i.group_id = ?
                """, new ArrayList<>(List.of(utc(now), groupId)), limit, cursor,
                (row, n) -> new InvitationResponse(
                        row.getObject("invitation_id", UUID.class),
                        row.getObject("group_id", UUID.class),
                        row.getString("invitee_email"),
                        InvitationStatus.valueOf(row.getString("status")),
                        instant(row, "created_at"),
                        instant(row, "expires_at")),
                InvitationResponse::createdAt, InvitationResponse::invitationId);
    }

    /**
     * Invitations waiting for the caller: PENDING, not expired, sent to their account email (any letter case),
     * for groups they don't already belong to. This is how the invitee "receives" an invitation in the app.
     */
    @Transactional(readOnly = true)
    public PageResponse<ReceivedInvitationResponse> listReceived(UUID userId, Integer limit, String cursor) {
        User user = users.findById(userId).orElseThrow(UserNotFoundException::new);
        Instant now = Instant.now();
        return page("""
                SELECT i.invitation_id, i.group_id, g.name AS group_name, i.invited_by,
                       inviter.display_name AS inviter_name, i.status, i.created_at, i.expires_at
                FROM invitations i
                JOIN groups g ON g.group_id = i.group_id
                JOIN users inviter ON inviter.user_id = i.invited_by
                WHERE lower(i.invitee_email) = lower(?) AND i.status = 'PENDING' AND i.expires_at > ?
                  AND NOT EXISTS (SELECT 1 FROM group_members m WHERE m.group_id = i.group_id AND m.user_id = ?)
                """, new ArrayList<>(List.of(user.getEmail(), utc(now), userId)), limit, cursor,
                (row, n) -> new ReceivedInvitationResponse(
                        row.getObject("invitation_id", UUID.class),
                        row.getObject("group_id", UUID.class),
                        row.getString("group_name"),
                        new ReceivedInvitationResponse.Inviter(row.getObject("invited_by", UUID.class),
                                row.getString("inviter_name")),
                        InvitationStatus.valueOf(row.getString("status")),
                        instant(row, "created_at"),
                        instant(row, "expires_at")),
                ReceivedInvitationResponse::createdAt, ReceivedInvitationResponse::invitationId);
    }

    /**
     * Runs {@code select} (whose WHERE clause is already open) newest first, one page at a time.
     * Same keyset pagination as the group list: one extra row tells whether another page exists.
     */
    private <T> PageResponse<T> page(String select, List<Object> args, Integer requestedLimit, String encodedCursor,
            RowMapper<T> mapper, Function<T, Instant> createdAt, Function<T, UUID> id) {
        int limit = PageResponse.clampLimit(requestedLimit);
        KeysetCursor after = KeysetCursor.decodeOrNull(encodedCursor);
        var sql = new StringBuilder(select);
        if (after != null) {
            sql.append("  AND (i.created_at, i.invitation_id) < (?, ?)\n");
            args.add(utc(after.createdAt()));
            args.add(after.id());
        }
        sql.append("ORDER BY i.created_at DESC, i.invitation_id DESC LIMIT ?");
        args.add(limit + 1);

        List<T> rows = jdbc.query(sql.toString(), mapper, args.toArray());
        if (rows.size() <= limit) {
            return new PageResponse<>(rows, null);
        }
        List<T> page = List.copyOf(rows.subList(0, limit));
        T last = page.get(limit - 1);
        return new PageResponse<>(page, new KeysetCursor(createdAt.apply(last), id.apply(last)).encode());
    }

    private boolean isMemberEmail(UUID groupId, String email) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM group_members m JOIN users u ON u.user_id = m.user_id
                               WHERE m.group_id = ? AND lower(u.email) = lower(?))
                """, Boolean.class, groupId, email));
    }

    private static OffsetDateTime utc(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }

    private static Instant instant(ResultSet row, String column) throws SQLException {
        return row.getObject(column, OffsetDateTime.class).toInstant();
    }

    static class AlreadyMemberException extends ApiProblem {
        AlreadyMemberException() {
            super(HttpStatus.CONFLICT, "ALREADY_MEMBER", "Already a member",
                    "A member of this group already uses this email address.");
        }
    }

    static class InvitationAlreadyPendingException extends ApiProblem {
        InvitationAlreadyPendingException() {
            super(HttpStatus.CONFLICT, "INVITATION_ALREADY_PENDING", "Invitation already pending",
                    "This email address already has a pending invitation to this group.");
        }
    }
}
