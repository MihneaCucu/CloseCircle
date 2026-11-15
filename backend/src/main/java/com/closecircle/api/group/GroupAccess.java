package com.closecircle.api.group;

import java.util.UUID;

import com.closecircle.api.error.ApiProblem;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Checks the caller's role in a group against current membership in the database.
 * The role is never taken from the request or the token.
 */
@Component
public class GroupAccess {
    private final GroupMemberRepository members;

    public GroupAccess(GroupMemberRepository members) {
        this.members = members;
    }

    /**
     * Returns the caller's role. A group that doesn't exist and a group the caller doesn't belong to
     * get the same 404, so outsiders can't discover which groups exist.
     */
    public MemberRole requireMember(UUID groupId, UUID userId) {
        return members.findById(new GroupMember.Id(groupId, userId))
                .map(GroupMember::getRole)
                .orElseThrow(GroupNotFoundException::new);
    }

    /** Members get 403: they may know the group exists, but this action needs OWNER or ADMIN. */
    public MemberRole requireOwnerOrAdmin(UUID groupId, UUID userId) {
        MemberRole role = requireMember(groupId, userId);
        if (role == MemberRole.MEMBER) {
            throw new InsufficientRoleException();
        }
        return role;
    }

    public static class GroupNotFoundException extends ApiProblem {
        GroupNotFoundException() {
            super(HttpStatus.NOT_FOUND, "GROUP_NOT_FOUND", "Group not found",
                    "The group does not exist or you are not a member of it.");
        }
    }

    public static class InsufficientRoleException extends ApiProblem {
        InsufficientRoleException() {
            super(HttpStatus.FORBIDDEN, "INSUFFICIENT_ROLE", "Insufficient role",
                    "Only the group's owner or an admin can do this.");
        }
    }
}
