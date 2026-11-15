package com.closecircle.api.group;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface GroupRepository extends JpaRepository<Group, UUID> {
}
