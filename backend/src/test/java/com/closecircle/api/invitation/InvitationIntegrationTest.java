package com.closecircle.api.invitation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import com.closecircle.api.PostgresIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/** SCRUM-12 acceptance criteria, checked through the HTTP API against a real database. */
class InvitationIntegrationTest extends PostgresIntegrationTest {

    // --- Criterion 1: a member (owner/admin) can send an invitation to another user ---

    @Test
    void ownerInvitesAnotherUserByEmail() throws Exception {
        TestUser owner = registerUser("Owner");
        UUID groupId = createGroup(owner, "Family");
        String email = uniqueEmail("Sam");

        JsonNode invitation = body(invite(owner, groupId, email)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.invitationId").isNotEmpty())
                .andExpect(jsonPath("$.groupId").value(groupId.toString()))
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.token").doesNotExist()));

        // Lasts 7 days.
        Instant created = Instant.parse(invitation.get("createdAt").asText());
        Instant expires = Instant.parse(invitation.get("expiresAt").asText());
        assertThat(Duration.between(created, expires)).isEqualTo(Duration.ofDays(7));

        // Only a SHA-256 hash of the token is stored.
        String hash = jdbc.queryForObject("SELECT token_hash FROM invitations WHERE invitation_id = ?",
                String.class, UUID.fromString(invitation.get("invitationId").asText()));
        assertThat(hash).matches("[0-9a-f]{64}");
    }

    @Test
    void adminCanInviteButPlainMemberCannot() throws Exception {
        TestUser owner = registerUser("Owner");
        TestUser admin = registerUser("Admin");
        TestUser member = registerUser("Member");
        UUID groupId = createGroup(owner, "Family");
        addMember(groupId, admin, "ADMIN");
        addMember(groupId, member, "MEMBER");

        invite(admin, groupId, uniqueEmail("Sam")).andExpect(status().isCreated());
        invite(member, groupId, uniqueEmail("Kim"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_ROLE"));
    }

    @Test
    void outsidersCannotTellWhetherAGroupExists() throws Exception {
        TestUser owner = registerUser("Owner");
        TestUser outsider = registerUser("Outsider");
        UUID groupId = createGroup(owner, "Family");

        invite(outsider, groupId, uniqueEmail("Sam"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("GROUP_NOT_FOUND"));
        invite(outsider, UUID.randomUUID(), uniqueEmail("Sam"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("GROUP_NOT_FOUND"));
    }

    @Test
    void invitingAnExistingMemberOrTheSamePersonTwiceIsAConflict() throws Exception {
        TestUser owner = registerUser("Owner");
        TestUser member = registerUser("Member");
        UUID groupId = createGroup(owner, "Family");
        addMember(groupId, member, "MEMBER");

        invite(owner, groupId, member.email().toUpperCase())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_MEMBER"));

        String email = uniqueEmail("Sam");
        invite(owner, groupId, email).andExpect(status().isCreated());
        invite(owner, groupId, email.toUpperCase())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVITATION_ALREADY_PENDING"));
    }

    @Test
    void databaseItselfAllowsOnlyOnePendingInvitationPerEmail() throws Exception {
        // Bypasses the service on purpose: migration V4's index must stop duplicates even if two requests race.
        TestUser owner = registerUser("Owner");
        UUID groupId = createGroup(owner, "Family");
        String insert = "INSERT INTO invitations (group_id, invited_by, invitee_email, token_hash, expires_at) "
                + "VALUES (?, ?, ?, ?, now() + interval '7 days')";
        jdbc.update(insert, groupId, owner.id(), "sam@example.invalid", UUID.randomUUID().toString());

        assertThatThrownBy(() -> jdbc.update(insert, groupId, owner.id(), "SAM@example.invalid",
                UUID.randomUUID().toString()))
                .isInstanceOf(DuplicateKeyException.class)
                .hasMessageContaining("invitations_one_pending_per_email");
    }

    @Test
    void invalidEmailIsRejected() throws Exception {
        TestUser owner = registerUser("Owner");
        UUID groupId = createGroup(owner, "Family");

        invite(owner, groupId, "not-an-email")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("email"));
    }

    // --- Criterion 2: the invitation is PENDING until answered ---

    @Test
    void ownerSeesTheInvitationAsPending() throws Exception {
        TestUser owner = registerUser("Owner");
        UUID groupId = createGroup(owner, "Family");
        String email = uniqueEmail("Sam");
        invite(owner, groupId, email).andExpect(status().isCreated());

        mvc.perform(get("/api/v1/groups/{groupId}/invitations", groupId).header("Authorization", owner.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].email").value(email))
                .andExpect(jsonPath("$.items[0].status").value("PENDING"))
                .andExpect(jsonPath("$.items[0].token").doesNotExist());
    }

    @Test
    void expiredInvitationIsReportedAsExpiredAndCanBeReplaced() throws Exception {
        TestUser owner = registerUser("Owner");
        UUID groupId = createGroup(owner, "Family");
        String email = uniqueEmail("Sam");
        TestUser sam = registerUser("Sam", email);
        UUID first = invitationId(invite(owner, groupId, email));
        jdbc.update("UPDATE invitations SET created_at = now() - interval '8 days', "
                + "expires_at = now() - interval '1 day' WHERE invitation_id = ?", first);

        mvc.perform(get("/api/v1/groups/{groupId}/invitations", groupId).header("Authorization", owner.bearer()))
                .andExpect(jsonPath("$.items[0].status").value("EXPIRED"));
        mvc.perform(get("/api/v1/invitations").header("Authorization", sam.bearer()))
                .andExpect(jsonPath("$.items", hasSize(0)));

        // A new invitation is allowed; the old row is now stored as EXPIRED.
        invite(owner, groupId, email).andExpect(status().isCreated());
        assertThat(jdbc.queryForObject("SELECT status FROM invitations WHERE invitation_id = ?", String.class, first))
                .isEqualTo("EXPIRED");
    }

    @Test
    void plainMemberCannotListTheGroupsInvitations() throws Exception {
        TestUser owner = registerUser("Owner");
        TestUser member = registerUser("Member");
        UUID groupId = createGroup(owner, "Family");
        addMember(groupId, member, "MEMBER");

        mvc.perform(get("/api/v1/groups/{groupId}/invitations", groupId).header("Authorization", member.bearer()))
                .andExpect(status().isForbidden());
    }

    // --- Criterion 3: the invitee receives the invitation and can view it ---

    @Test
    void inviteeSeesTheInvitationWithGroupAndInviter() throws Exception {
        TestUser owner = registerUser("Owner");
        UUID groupId = createGroup(owner, "Weekend crew");
        String email = uniqueEmail("Sam");
        TestUser sam = registerUser("Sam", email);
        TestUser someoneElse = registerUser("Kim");
        // Invited with different letter case than the account email.
        UUID invitationId = invitationId(invite(owner, groupId, email.toUpperCase()));

        mvc.perform(get("/api/v1/invitations").header("Authorization", sam.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].invitationId").value(invitationId.toString()))
                .andExpect(jsonPath("$.items[0].groupId").value(groupId.toString()))
                .andExpect(jsonPath("$.items[0].groupName").value("Weekend crew"))
                .andExpect(jsonPath("$.items[0].invitedBy.userId").value(owner.id().toString()))
                .andExpect(jsonPath("$.items[0].invitedBy.displayName").value("Owner"))
                .andExpect(jsonPath("$.items[0].invitedBy.email").doesNotExist())
                .andExpect(jsonPath("$.items[0].status").value("PENDING"));

        mvc.perform(get("/api/v1/invitations").header("Authorization", someoneElse.bearer()))
                .andExpect(jsonPath("$.items", hasSize(0)));
    }

    @Test
    void personInvitedBeforeRegisteringSeesItAfterRegistering() throws Exception {
        TestUser owner = registerUser("Owner");
        UUID groupId = createGroup(owner, "Family");
        String email = uniqueEmail("Sam");
        invite(owner, groupId, email).andExpect(status().isCreated());

        TestUser sam = registerUser("Sam", email);
        mvc.perform(get("/api/v1/invitations").header("Authorization", sam.bearer()))
                .andExpect(jsonPath("$.items", hasSize(1)));
    }

    @Test
    void invitationEndpointsRequireAToken() throws Exception {
        mvc.perform(get("/api/v1/invitations")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/groups/{groupId}/invitations", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"a@example.invalid\"}"))
                .andExpect(status().isUnauthorized());
    }

    private UUID createGroup(TestUser user, String name) throws Exception {
        JsonNode group = body(postJsonAs(user, "/api/v1/groups", Map.of("name", name))
                .andExpect(status().isCreated()));
        return UUID.fromString(group.get("groupId").asText());
    }

    /** There is no API for adding members yet (that's SCRUM-13), so tests insert the membership directly. */
    private void addMember(UUID groupId, TestUser user, String role) {
        jdbc.update("INSERT INTO group_members (group_id, user_id, member_role) VALUES (?, ?, ?)",
                groupId, user.id(), role);
    }

    private ResultActions invite(TestUser inviter, UUID groupId, String email) throws Exception {
        return postJsonAs(inviter, "/api/v1/groups/" + groupId + "/invitations", Map.of("email", email));
    }

    private UUID invitationId(ResultActions created) throws Exception {
        return UUID.fromString(body(created.andExpect(status().isCreated())).get("invitationId").asText());
    }

    private ResultActions postJsonAs(TestUser user, String path, Object body) throws Exception {
        return mvc.perform(post(path).header("Authorization", user.bearer())
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)));
    }
}
