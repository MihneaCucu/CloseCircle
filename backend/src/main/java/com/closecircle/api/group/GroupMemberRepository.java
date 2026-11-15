package com.closecircle.api.group;

import org.springframework.data.jpa.repository.JpaRepository;

public interface GroupMemberRepository extends JpaRepository<GroupMember, GroupMember.Id> {
}
