package com.closecircle.api.group;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.closecircle.api.pagination.KeysetCursor;
import com.closecircle.api.pagination.PageResponse;
import com.closecircle.api.user.UserNotFoundException;
import com.closecircle.api.user.UserRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GroupService {
    private final GroupRepository groups;
    private final GroupMemberRepository members;
    private final UserRepository users;
    private final JdbcTemplate jdbc;

    public GroupService(GroupRepository groups, GroupMemberRepository members, UserRepository users,
            JdbcTemplate jdbc) {
        this.groups = groups;
        this.members = members;
        this.users = users;
        this.jdbc = jdbc;
    }

    /**
     * Creates the group and makes the creator its OWNER. Both rows are written in one transaction:
     * if the membership insert fails, the group is rolled back too, so there is never a group without an owner.
     */
    @Transactional
    public GroupResponse create(UUID userId, CreateGroupRequest request) {
        // The token can outlive its account; fail with a clear 404 instead of a foreign-key error.
        if (!users.existsById(userId)) {
            throw new UserNotFoundException();
        }
        Instant now = Instant.now();
        Group group = groups.saveAndFlush(new Group(request.name(), userId, now));
        members.save(new GroupMember(group.getId(), userId, MemberRole.OWNER, now));
        return new GroupResponse(group.getId(), group.getName(), group.getCreatedBy(), group.getCreatedAt(),
                MemberRole.OWNER, 1);
    }

    /**
     * Groups the user belongs to, newest first, with the user's role and each group's member count.
     * Plain SQL because it joins, counts and pages in one query.
     */
    @Transactional(readOnly = true)
    public PageResponse<GroupResponse> listForUser(UUID userId, Integer requestedLimit, String cursor) {
        int limit = PageResponse.clampLimit(requestedLimit);
        KeysetCursor after = KeysetCursor.decodeOrNull(cursor);

        var sql = new StringBuilder("""
                SELECT g.group_id, g.name, g.created_by, g.created_at, m.member_role,
                       (SELECT count(*) FROM group_members c WHERE c.group_id = g.group_id) AS member_count
                FROM groups g
                JOIN group_members m ON m.group_id = g.group_id AND m.user_id = ?
                """);
        List<Object> args = new ArrayList<>(List.of(userId));
        if (after != null) {
            // Row comparison: everything strictly after the last item of the previous page.
            sql.append("WHERE (g.created_at, g.group_id) < (?, ?)\n");
            args.add(OffsetDateTime.ofInstant(after.createdAt(), ZoneOffset.UTC));
            args.add(after.id());
        }
        sql.append("ORDER BY g.created_at DESC, g.group_id DESC LIMIT ?");
        // Ask for one extra row: if it comes back, there is another page.
        args.add(limit + 1);

        List<GroupResponse> rows = jdbc.query(sql.toString(), GroupService::toResponse, args.toArray());
        if (rows.size() <= limit) {
            return new PageResponse<>(rows, null);
        }
        List<GroupResponse> page = rows.subList(0, limit);
        GroupResponse last = page.get(limit - 1);
        return new PageResponse<>(List.copyOf(page), new KeysetCursor(last.createdAt(), last.groupId()).encode());
    }

    private static GroupResponse toResponse(ResultSet row, int rowNumber) throws SQLException {
        return new GroupResponse(
                row.getObject("group_id", UUID.class),
                row.getString("name"),
                row.getObject("created_by", UUID.class),
                row.getObject("created_at", OffsetDateTime.class).toInstant(),
                MemberRole.valueOf(row.getString("member_role")),
                row.getLong("member_count"));
    }
}
