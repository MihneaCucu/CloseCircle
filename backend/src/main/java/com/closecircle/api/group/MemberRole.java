package com.closecircle.api.group;

/** Allowed values of {@code group_members.member_role} (database check constraint group_members_role_ck). */
public enum MemberRole {
    OWNER,
    ADMIN,
    MEMBER
}
