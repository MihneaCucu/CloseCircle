package com.closecircle.api.sharing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Runs the sharing API against the real Flyway schema. Skipped when Docker is unavailable. */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class SharingIntegrationTest {
    private static final String PATH = "/api/v1/groups/{groupId}/me/sharing";

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    // Set both the application and Flyway connections so no developer environment variable
    // can point the tests at a shared database.
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("FLYWAY_DATABASE_URL", POSTGRES::getJdbcUrl);
        registry.add("FLYWAY_DATABASE_USERNAME", POSTGRES::getUsername);
        registry.add("FLYWAY_DATABASE_PASSWORD", POSTGRES::getPassword);
    }

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private SharingService sharing;

    private UUID groupId;
    private UUID otherGroupId;
    private UUID alice;
    private UUID bob;
    private UUID outsider;

    @BeforeEach
    void seedGroups() {
        alice = insertUser("alice");
        bob = insertUser("bob");
        outsider = insertUser("outsider");
        groupId = insertGroup("Family", alice);
        otherGroupId = insertGroup("Friends", alice);
        addMember(groupId, alice, "OWNER");
        addMember(groupId, bob, "MEMBER");
        addMember(otherGroupId, alice, "OWNER");
        addMember(otherGroupId, bob, "MEMBER");
    }

    @Test
    void newMemberDoesNotShareUntilTheyEnableIt() throws Exception {
        mvc.perform(get(PATH, groupId).with(as(alice)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sharingEnabled").value(false))
                .andExpect(jsonPath("$.updatedAt").doesNotExist());

        assertThat(sharing.canViewLocation(groupId, bob, alice)).isFalse();
    }

    @Test
    void sharingIsPersistedSeparatelyForEachGroup() throws Exception {
        update(groupId, alice, true, true)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sharingEnabled").value(true))
                .andExpect(jsonPath("$.updatedAt").isNotEmpty());

        mvc.perform(get(PATH, groupId).with(as(alice)))
                .andExpect(jsonPath("$.sharingEnabled").value(true));
        mvc.perform(get(PATH, otherGroupId).with(as(alice)))
                .andExpect(jsonPath("$.sharingEnabled").value(false));

        assertThat(sharing.canViewLocation(groupId, bob, alice)).isTrue();
        assertThat(sharing.canViewLocation(otherGroupId, bob, alice)).isFalse();
        assertThat(sharing.findSharingMemberIds(groupId)).containsExactly(alice);
    }

    @Test
    void disablingSharingHidesTheMemberAgain() throws Exception {
        update(groupId, alice, true, true).andExpect(status().isOk());
        update(groupId, alice, false, true).andExpect(status().isOk());

        assertThat(sharing.canViewLocation(groupId, bob, alice)).isFalse();
        assertThat(sharing.findSharingMemberIds(groupId)).isEmpty();
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM shares WHERE group_id = ? AND user_id = ?", Integer.class, groupId, alice))
                .isEqualTo(1);
    }

    @Test
    void nonMemberCannotReadOrChangeSettingsOrViewLocations() throws Exception {
        mvc.perform(get(PATH, groupId).with(as(outsider))).andExpect(status().isNotFound());
        update(groupId, outsider, true, true).andExpect(status().isNotFound());
        update(groupId, alice, true, true).andExpect(status().isOk());

        assertThat(sharing.canViewLocation(groupId, outsider, alice)).isFalse();
    }

    @Test
    void unknownGroupLooksTheSameAsAGroupTheCallerIsNotIn() throws Exception {
        mvc.perform(get(PATH, UUID.randomUUID()).with(as(alice)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("GROUP_NOT_FOUND"));
    }

    private org.springframework.test.web.servlet.ResultActions update(
            UUID group, UUID user, boolean sharingEnabled, boolean shareLiveLocation) throws Exception {
        return mvc.perform(put(PATH, group).with(as(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sharingEnabled\":%s,\"shareLiveLocation\":%s}"
                        .formatted(sharingEnabled, shareLiveLocation)));
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor as(UUID userId) {
        return jwt().jwt(token -> token.subject(userId.toString()));
    }

    private UUID insertUser(String name) {
        return jdbc.queryForObject("""
                INSERT INTO users (email, password_hash, display_name)
                VALUES (?, 'not-a-real-hash', ?) RETURNING user_id
                """, UUID.class, name + "-" + UUID.randomUUID() + "@example.invalid", name);
    }

    private UUID insertGroup(String name, UUID createdBy) {
        return jdbc.queryForObject(
                "INSERT INTO groups (name, created_by) VALUES (?, ?) RETURNING group_id",
                UUID.class, name, createdBy);
    }

    private void addMember(UUID group, UUID user, String role) {
        jdbc.update("INSERT INTO group_members (group_id, user_id, member_role) VALUES (?, ?, ?)",
                group, user, role);
    }
}
